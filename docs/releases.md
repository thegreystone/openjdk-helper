# Releases and binary signing

Run all commands from the repository root. See [development](development.md) for local builds and tests, the [plugin guide](plugin.md) for portable plugin packaging, [deployment](deployment.md) for containers and reverse proxies, and the [main README](../README.md#installing) for client installation.

## Releases

[GitHub Releases](https://github.com/thegreystone/openjdk-helper/releases) are produced by pushing a `vX.Y.Z` tag. The [release workflow](../.github/workflows/release.yml) uses the tag without its `v` prefix as the release version, rather than the snapshot version in the working tree.

Before pushing the tag, set `version` in [plugin/plugin.json](../plugin/plugin.json) to `X.Y.Z` and commit it. The plugin job fails when the committed version differs from the tag, because marketplace installs read that file from Git. The Claude manifest in `plugin/.claude-plugin/plugin.json` has no version and needs no change; see the [plugin guide](plugin.md#versions-and-updates).

The workflow builds and tests:

- An uber-jar for Java 25 or later.
- Native executables and MCP Bundles for Linux x86_64/aarch64, macOS aarch64 and Windows x86_64.
- The ChatGPT and Codex plugin ZIP, which connects to the hosted MCP service. Claude installs the plugin directory from Git and uses no release artifact.

The GitHub release waits for the uber-jar, native/bundle, plugin and JVM Docker jobs to succeed. Native and bundle test failures therefore prevent release publication. Normal builds do not publish images.

Release assets use these names, with `<version>` replaced by the tag's version:

| Artifact | Filename |
| --- | --- |
| Uber-jar | `openjdk-helper-<version>-runner.jar` |
| Linux/macOS executable | `openjdk-helper-<version>-<platform>` |
| Windows executable | `openjdk-helper-<version>-windows-x86_64.exe` |
| MCP Bundle | `openjdk-helper-<version>-<platform>.mcpb` |
| Portable plugin | `openjdk-helper-<version>-plugin.zip` |

The platform suffixes are `linux-x86_64`, `linux-aarch64`, `macos-aarch64` and `windows-x86_64`. Local native builds use the `-runner` name until the release workflow renames them.

### Release container images

The tag-triggered workflow publishes multi-architecture images for `linux/amd64` and `linux/arm64`, using the `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` repository secrets:

| Image tags | Contents |
| --- | --- |
| `greystone/openjdk-helper:<version>`, `greystone/openjdk-helper:latest` | JVM fast-jar on the Temurin 25 JRE image |
| `greystone/openjdk-helper:<version>-native`, `greystone/openjdk-helper:native` | Release native executables on the minimal UBI 9 base |

The native-image job uses [Dockerfile.native-release](../src/main/docker/Dockerfile.native-release), starts both architectures (ARM64 under QEMU), and checks `/version` and a census lookup before pushing the image. This job is independent of the final GitHub release job: its failure does not prevent the other release assets from being published. `latest` is updated by the release workflow, not on every commit to `main`.

Container build, manual image publication and runtime configuration commands are in [deployment](deployment.md).

## Signing release binaries

### macOS

The release workflow signs the macOS binary with a Developer ID certificate, hardened runtime and timestamp, then notarizes it before packing the bundle. Both the standalone executable and the executable inside the bundle carry that signature.

The workflow requires these repository secrets:

- `MACOS_CERTIFICATE`
- `MACOS_CERTIFICATE_PWD`
- `MACOS_SIGNING_IDENTITY`
- `NOTARIZATION_APPLE_ID`
- `NOTARIZATION_PASSWORD`
- `NOTARIZATION_TEAM_ID`

A bare executable cannot be stapled, so Gatekeeper verifies the notarization ticket online on first launch. The workflow verifies the signature and removes its temporary signing keychain afterwards.

### Windows

The release workflow initially publishes the Windows binary and bundle unsigned. They are signed afterwards on the maintainer's machine using a Certum code-signing certificate through SimplySign Desktop, which requires an interactive OTP login.

Before running [Sign-Release.ps1](../mcpb/Sign-Release.ps1), log in to SimplySign Desktop, authenticate `gh` for the repository, install the Windows SDK's `signtool`, and install Node.js 22 or later and the bundle tools:

```powershell
npm ci --prefix mcpb --ignore-scripts
```

Replace `<version>` with the published release version without the `v` prefix:

```powershell
# Sign, pack and test locally without replacing release assets
.\mcpb\Sign-Release.ps1 -Version "<version>" -NoUpload

# Sign, pack, test and replace the Windows release assets
.\mcpb\Sign-Release.ps1 -Version "<version>"
```

The script downloads the Windows binary from the release, signs it with SHA-256 and a Certum RFC 3161 timestamp, verifies the signature and timestamp, repacks the Windows `.mcpb` around the signed binary with [pack.mjs](../mcpb/pack.mjs), and runs [test-mcpb.mjs](../mcpb/test-mcpb.mjs) against the bundle. Without `-NoUpload`, it replaces both release assets. With `-NoUpload`, the signed artifacts remain under `target/signed` for inspection.

The certificate thumbprint comes from `$env:CERTUM_SIGN_THUMBPRINT`, falling back to `$env:DISKSPACE_SIGN_THUMBPRINT`, or can be supplied with `-Thumbprint`. Keeping the environment variable in the maintainer's PowerShell profile makes certificate renewal a profile edit rather than a commit. Always pin the certificate by thumbprint; the script does not use automatic certificate selection.

The `.mcpb` container itself and the Linux binaries remain unsigned. Signing the executable inside a bundle does not sign the bundle file.
