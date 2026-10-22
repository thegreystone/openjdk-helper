[![Build](https://github.com/thegreystone/openjdk-helper/actions/workflows/build.yml/badge.svg)](https://github.com/thegreystone/openjdk-helper/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/thegreystone/openjdk-helper)](https://github.com/thegreystone/openjdk-helper/releases/latest)
[![MCP](https://img.shields.io/badge/MCP-server-teal)](https://modelcontextprotocol.io/)
[![Docker Pulls](https://img.shields.io/docker/pulls/greystone/openjdk-helper)](https://hub.docker.com/r/greystone/openjdk-helper)
[![Docker Image Version](https://img.shields.io/docker/v/greystone/openjdk-helper?sort=semver&label=docker)](https://hub.docker.com/r/greystone/openjdk-helper)

# OpenJDK Helper

<img src="plugin/assets/duke-icon.png" alt="Duke-style OpenJDK Helper mascot" width="128" align="right">

OpenJDK Helper gives an AI assistant reliable, source-backed answers about OpenJDK. Ask Claude or Codex who the Reviewers of a project are, whether a fix has been backported to 17u and 21u, what a JEP's status is, which pull requests are waiting for review, when JDK 27 enters rampdown, or how a Committer election works, and it answers from the census, JBS, GitHub, the JEP index, the release pages, the mailing list archives and the Developers' Guide, with links to the sources.

It is built for OpenJDK contributors and project leads. Every tool is read-only and reports what the sources say, including when data is partial or unavailable, rather than guessing. The same 27 tools are available as an MCP server for AI clients and as a REST/OpenAPI service, which also powers the [OpenJDK Project Assistant](https://chatgpt.com/g/g-cdK5pudqC-openjdk-project-assistant) Custom GPT.

Things to try once it is installed:

- "Who are the Reviewers in the JMC project?"
- "Where has JDK-8394772 been fixed, and which backports are still open?"
- "Triage the open pull requests in openjdk/jmc."
- "What phase is JDK 27 in, and when is General Availability?"
- "Summarize what was integrated in openjdk/jmc last quarter."
- "What does the Developers' Guide say about sponsoring a change?"

## Installing

There are two ways to use the server from an AI client. The **hosted service** at `https://api.hirt.se/openjdk/mcp` needs nothing installed. A **local install** runs the native executable from the [Releases page](https://github.com/thegreystone/openjdk-helper/releases/latest) on your own machine over stdio, so upstream requests go out from your network and your own `OPENJDK_GITHUB_TOKEN` can be used. Both expose the same 27 tools.

Release files, substituting the version you downloaded:

| Platform | Native executable | MCP Bundle |
| --- | --- | --- |
| Linux x86_64 | `openjdk-helper-0.2.0-linux-x86_64` | `openjdk-helper-0.2.0-linux-x86_64.mcpb` |
| Linux aarch64 | `openjdk-helper-0.2.0-linux-aarch64` | `openjdk-helper-0.2.0-linux-aarch64.mcpb` |
| macOS Apple Silicon | `openjdk-helper-0.2.0-macos-aarch64` | `openjdk-helper-0.2.0-macos-aarch64.mcpb` |
| Windows x86_64 | `openjdk-helper-0.2.0-windows-x86_64.exe` | `openjdk-helper-0.2.0-windows-x86_64.mcpb` |

The macOS executable is signed and notarized; the Windows executable is Authenticode-signed. `openjdk-helper-0.2.0-runner.jar` is the same server for any platform with Java 25 or later. A local executable started with `-Dquarkus.profile=stdio` speaks MCP on stdin/stdout, keeps the HTTP listener off so several clients can each launch their own process, and logs to stderr.

### Claude Code

**Hosted:**

```sh
claude mcp add --transport http openjdk-helper https://api.hirt.se/openjdk/mcp
```

**Local**, pointing at the downloaded executable:

```sh
# macOS or Linux
claude mcp add openjdk-helper -- /path/to/openjdk-helper-0.2.0-macos-aarch64 -Dquarkus.profile=stdio

# Windows
claude mcp add openjdk-helper -- C:\tools\openjdk-helper\openjdk-helper-0.2.0-windows-x86_64.exe -Dquarkus.profile=stdio
```

Both commands register the server for the current project only. Add `--scope user` to make it available in every project on your machine, or `--scope project` to write it to a `.mcp.json` file in the repository that teammates get when they clone it. A `.mcp.json` for the hosted service looks like this:

```json
{
  "mcpServers": {
    "openjdk-helper": {
      "type": "http",
      "url": "https://api.hirt.se/openjdk/mcp"
    }
  }
}
```

Run `/mcp` inside Claude Code to check the connection and list the tools, and `claude mcp remove openjdk-helper` to take it out again. Claude Code launches the process from a normal working directory, so the `-Duser.dir` workaround is not needed.

### Claude Desktop

**Hosted:** open *Settings → Connectors → Add custom connector*, name it `OpenJDK Helper` and enter the URL `https://api.hirt.se/openjdk/mcp`. Custom connectors are available on the Claude plans that support them; no authentication is needed.

**Local:** download the `.mcpb` bundle for your platform, then double-click it or open it from *Settings → Extensions → Advanced settings → Install Extension…*. Claude Desktop shows its standard unsigned-extension notice, because the bundle file itself carries no signature (the executable inside it is signed); confirm with *Install*. There is nothing to configure, and no Java is needed. Start a new chat and the tools appear in the tool list.

If you prefer editing the config file instead of installing the bundle, add the executable to `claude_desktop_config.json` (`%APPDATA%\Claude\claude_desktop_config.json` on Windows, `~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "openjdk-helper": {
      "command": "C:\\tools\\openjdk-helper\\openjdk-helper-0.2.0-windows-x86_64.exe",
      "args": ["-Dquarkus.profile=stdio"]
    }
  }
}
```

If Claude Desktop starts the process in a directory it cannot write to, add `-Duser.dir=C:\\tools\\openjdk-helper` to the arguments. For the jar, use `java` as the command with the arguments `-Dquarkus.profile=stdio`, `-jar` and the absolute jar path. Restart Claude Desktop afterwards.

### Codex CLI

**Hosted**, in `~/.codex/config.toml` (recent Codex versions support Streamable HTTP servers by URL; the equivalent command is `codex mcp add openjdk-helper --url https://api.hirt.se/openjdk/mcp`):

```toml
[mcp_servers.openjdk-helper]
url = "https://api.hirt.se/openjdk/mcp"
```

**Local**, with the downloaded executable:

```toml
[mcp_servers.openjdk-helper]
command = "/path/to/openjdk-helper-0.2.0-linux-x86_64"
args = ["-Dquarkus.profile=stdio"]
```

Or let the CLI write it:

```sh
codex mcp add openjdk-helper -- /path/to/openjdk-helper-0.2.0-linux-x86_64 -Dquarkus.profile=stdio
```

Then `codex mcp list` should show `openjdk-helper`.

### Other MCP clients

Any client that speaks **Streamable HTTP** can use `https://api.hirt.se/openjdk/mcp`. Legacy SSE clients can use `https://api.hirt.se/openjdk/mcp/sse`, and [connection information](https://api.hirt.se/openjdk/mcp-info) is available from the service. Clients that launch a local process use the executable with `-Dquarkus.profile=stdio` as shown above.

### Claude, ChatGPT and Codex plugin

The plugin combines OpenJDK contributor and project-lead instructions with the hosted MCP connection, in one directory that Claude Code, Claude Desktop, ChatGPT and Codex can all install. The [AI Plugins marketplace](https://github.com/thegreystone/ai-plugins) provides installation instructions for each client. See the [plugin guide](docs/plugin.md) for local testing and packaging. Native `.mcpb` bundles provide local stdio installations.

## Public service

The REST service is deployed at `https://api.hirt.se/openjdk`:

- [Version](https://api.hirt.se/openjdk/version)
- [Swagger UI](https://api.hirt.se/openjdk/swagger-ui/)
- [OpenAPI schema](https://api.hirt.se/openjdk/openapi/)
- [Example census lookup](https://api.hirt.se/openjdk/census/people?userid=hirt)

The hosted service is free and provided on a best-effort basis under the [terms of service](TERMS.md). See the [privacy notice](PRIVACY.md) for request processing, upstream services, caching, server logging and the differences between hosted and local use.

## Documentation

- [Tools and workflows](docs/tools.md): all 27 tools, contributor and project-lead examples, and REST endpoints.
- [Configuration](docs/configuration.md): local server settings, optional upstream tokens and availability limits.
- [Development and operations](docs/README.md): builds, tests, native images, packaging, deployment and releases.

## License

The software is available under the [BSD 3-Clause license](LICENSE).
