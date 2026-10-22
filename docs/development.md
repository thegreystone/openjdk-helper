# Building and developing OpenJDK Helper

Run all commands from the repository root. For client installation, see the [main README](../README.md#installing). Container builds and reverse-proxy configuration are covered in [deployment](deployment.md); portable ChatGPT and Codex plugin packaging is covered in the [plugin guide](plugin.md).

Use JDK 25 or later. In PowerShell or Command Prompt, use `mvnw.cmd` instead of `./mvnw` (in PowerShell, invoke it as `.\mvnw.cmd`).

Replace `<version>` in the filenames and commands below with the version of the build in [pom.xml](../pom.xml). You can print that value with:

```sh
./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout
```

## Build and test

```sh
# REST, parsing and MCP tests, followed by packaged JVM protocol checks
./mvnw verify -DskipITs=false

# Format the Java sources; the build's validate phase fails on unformatted code
./mvnw spotless:apply

# Development mode
./mvnw quarkus:dev

# Run the default fast-jar package (keep the whole quarkus-app directory)
java -jar target/quarkus-app/quarkus-run.jar
```

Tests use local census XML, GitHub, JBS, JEP, release-page, mailing-list, Developers' Guide and repository-document fixtures, independent of upstream availability and membership changes. The older census HTML parser retains compatibility tests. Packaged JVM and native tests exercise REST/OpenAPI and all 27 tools over HTTP and stdio, including error handling and non-JSON stdout rejection. The MCPB test also verifies that process summaries are present in the installed native artifact.

The [build workflow](../.github/workflows/build.yml) runs JVM verification, Linux native verification, and packaging checks for MCP Bundles and the portable plugin. The Java formatting configuration is in [config/formatter/openjdk-helper-formatting.xml](../config/formatter/openjdk-helper-formatting.xml).

## Native executable

Install GraalVM for JDK 25 and the platform C/C++ toolchain. On Windows, use Visual Studio C++ Build Tools with the Windows SDK, or its x64 Native Tools command prompt. Set `JAVA_HOME` to GraalVM and put its `bin` directory on `PATH`.

```sh
./mvnw verify -Dnative
```

This builds `target/openjdk-helper-<version>-runner` (`.exe` on Windows) and runs the packaged protocol checks against that executable. No JVM is needed to run it. Unused AWT/image-processing dependencies have been removed, so no font libraries or AWT sidecars are required.

The [application configuration](../src/main/resources/application.properties) selects a locally installed GraalVM by default, includes all character sets and the project-process catalog, and uses the compatibility CPU target. Container image creation is opt-in.

Alternatively, build a Linux binary with Docker or Podman:

```sh
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

Run native `verify` on a matching host platform; a container-built Linux binary cannot execute directly on Windows or macOS. See [deployment](deployment.md) for packaging and running the server in a container.

## MCP Bundles

Each `.mcpb` bundle wraps the native executable for its platform and launches it with the `stdio` profile. It needs no Java installation or port configuration; the local process fetches OpenJDK, GitHub and JBS data as needed. Public sources work without credentials where permitted. See [configuration](configuration.md) for upstream configuration, optional tokens and access constraints.

Install Node.js 22 or later for the packaging tools, and compile the matching native executable first. For a Windows build:

```powershell
npm ci --prefix mcpb --ignore-scripts
node mcpb/pack.mjs "<version>" windows-x86_64 "target/openjdk-helper-<version>-runner.exe"
node mcpb/test-mcpb.mjs "target/openjdk-helper-<version>-windows-x86_64.mcpb"
```

Use the matching suffix and binary for each platform:

| Platform | Packaging suffix | Native build filename |
| --- | --- | --- |
| Linux x86_64 | `linux-x86_64` | `openjdk-helper-<version>-runner` |
| Linux aarch64 | `linux-aarch64` | `openjdk-helper-<version>-runner` |
| macOS Apple Silicon | `macos-aarch64` | `openjdk-helper-<version>-runner` |
| Windows x86_64 | `windows-x86_64` | `openjdk-helper-<version>-runner.exe` |

[pack.mjs](../mcpb/pack.mjs) validates the manifest and executable architecture. The [smoke test](../mcpb/test-mcpb.mjs) extracts the finished archive and launches its manifest command to check MCP initialization, tool discovery and census lookup, clean stdout, and disabled HTTP binding. Node.js is only needed to build and test bundles, not to use them.

Release signing and bundle publication are described in [releases](releases.md).

## Running a build you made yourself over stdio

Build an uber-jar with Java 25 or later, then start it with the `stdio` profile:

```sh
./mvnw verify -Duber-jar -DskipITs=false
java -Dquarkus.profile=stdio -jar "target/openjdk-helper-<version>-runner.jar"
```

For a native executable built as described above:

```sh
"./target/openjdk-helper-<version>-runner" -Dquarkus.profile=stdio
```

On Windows:

```powershell
& ".\target\openjdk-helper-<version>-runner.exe" -Dquarkus.profile=stdio
```

The profile enables MCP on stdin/stdout, disables the HTTP listener and sends logs to stderr. Point the [client configurations in the README](../README.md#installing) at that jar or executable instead of a release download.
