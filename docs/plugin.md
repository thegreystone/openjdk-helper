# Claude, ChatGPT and Codex plugin

The [plugin source directory](../plugin/) is one plugin in two formats. It pairs the `openjdk-helper` skill with the hosted MCP server at `https://api.hirt.se/openjdk/mcp`. No Java runtime or local server is needed by plugin users. The existing [MCPB bundles](../mcpb/) provide the native local stdio server; REST/OpenAPI remains available for Custom GPT Actions and other clients.

| Client | Files read | Format |
| --- | --- | --- |
| Claude Code, Claude Desktop | `.claude-plugin/plugin.json`, `.mcp.json`, `skills/`, `assets/` | [Claude plugin manifest](https://code.claude.com/docs/en/plugins/manifest-reference) |
| ChatGPT, Codex | `plugin.json`, `mcp.json`, `skills/`, `assets/` | OpenAI [package format](https://developers.openai.com/plugins/build/plugins) (checked on 2026-10-21) |

Each client ignores the other's files. The skill, icon and license are shared. The two MCP declarations differ only in the transport name: OpenAI uses `streamable-http`, Claude uses `http`. The packager cross-checks that both manifests name the same plugin and both MCP files point at the same URL.

Users install from the [AI Plugins marketplace](https://github.com/thegreystone/ai-plugins), whose catalogs point at this repository's `plugin/` directory on `main` with a Git subdirectory source. Its README has the installation commands for each client.

## Versions and updates

Claude Code computes a version for every installed plugin and only refetches when that version changes. For a Git subdirectory source with no `version` in the manifest or the marketplace entry, the version is the commit SHA, so every push to `main` reaches Claude users on `claude plugin update`. For that reason `.claude-plugin/plugin.json` deliberately has no `version` field, and the packager rejects one. Pinning a version would freeze users on the cached copy until the string changes, and CI cannot stamp it, because Git installs read the committed files.

The portable `plugin.json` declares a version for Git-backed Codex marketplace installs. Keep it aligned with [pom.xml](../pom.xml); ZIP packaging overwrites it with the POM version or the explicit release override.

## Validate, test and build

Use Node.js 22 or later. Run all commands in this guide from the repository root:

```sh
claude plugin validate plugin
npm ci --prefix plugin-tools --ignore-scripts
node --test plugin-tools/test-plugin.mjs
node plugin-tools/pack.mjs
```

`claude plugin validate plugin` checks the Claude manifest, skill and MCP configuration; add `--profile directory` to also run the checks Anthropic's plugin directory applies to a submission. The expected output is one warning about the missing version, which is intentional. CI runs the same validation; it does not use `--strict`, because that would turn the version warning into a failure.

The Node tooling lives in [plugin-tools/](../plugin-tools/) rather than in the plugin directory. Claude Code runs `npm install` inside any plugin whose root has a `package.json` and lockfile, and the development files should not reach users of either client.

The packager output is `target/openjdk-helper-<version>-plugin.zip`. The version comes from [pom.xml](../pom.xml); `node plugin-tools/pack.mjs --version 0.2.1` overrides it for a release without changing source files. Packaging does not build or deploy the Java service. CI validates and builds the package, and the release workflow attaches the ZIP to GitHub releases.

The archive is OpenAI-only and contains:

```text
plugin.json
mcp.json
skills/openjdk-helper/SKILL.md
assets/duke-icon.png
LICENSE
```

Edit [plugin/plugin.json](../plugin/plugin.json) for OpenAI listing and review information, [plugin/.claude-plugin/plugin.json](../plugin/.claude-plugin/plugin.json) for Claude listing information, [plugin/mcp.json](../plugin/mcp.json) and [plugin/.mcp.json](../plugin/.mcp.json) for the server connection, and [plugin/skills/openjdk-helper/SKILL.md](../plugin/skills/openjdk-helper/SKILL.md) for assistant behavior. To include separate knowledge files, extend the [packager's explicit file list](../plugin-tools/pack.mjs) and its [archive test](../plugin-tools/test-plugin.mjs).

The packager checks this project's expected structure, metadata and safe archive paths. It is not a complete implementation of the official JSON schemas or a substitute for the portal's checks.

## Test the complete plugin

### Claude Code

The marketplace fetches `plugin/` from GitHub, so unpushed changes need a local marketplace. Create a directory with a `.claude-plugin/marketplace.json` that lists a copy of the plugin by relative path:

```json
{
  "name": "openjdk-helper-dev",
  "owner": { "name": "Marcus Hirt" },
  "plugins": [{ "name": "openjdk-helper", "source": "./openjdk-helper" }]
}
```

Copy `plugin/` to `openjdk-helper/` beside it, then:

```sh
claude plugin marketplace add <that directory>
claude plugin install openjdk-helper@openjdk-helper-dev
claude plugin details openjdk-helper@openjdk-helper-dev
claude mcp list
```

`details` should list one skill and one MCP server, and `mcp list` should show `plugin:openjdk-helper:openjdk-helper` as connected. A relative-path plugin loads in place, so later edits to the copy take effect at the next session start or `/reload-plugins`. Remove the development marketplace with `claude plugin marketplace remove openjdk-helper-dev` when done.

### ChatGPT and Codex

Copy [plugin-tools/marketplace.json](../plugin-tools/marketplace.json) to `.agents/plugins/marketplace.json` at the repository root, merging its entry if you already have a marketplace. Its `./plugin` source path is relative to the repository root. Install from that local marketplace in the Plugins Directory, then enable it in a fresh conversation. Reinstall after editing: local installs use a cached copy.

Run the prompts in [plugin/plugin.json](../plugin/plugin.json) under `extensions.com.openai.review.test_cases`, and record tool selection, arguments, results and source links. Confirm that the skill handles partial upstream data and that unsupported requests cause no writes. The positive cases describe expected behavior; they are not a record of successful end-to-end runs. See OpenAI's [connection and evaluation guide](https://developers.openai.com/plugins/deploy/connect-chatgpt).

To test just the server in ChatGPT, use Plugins → Add custom MCP server, enter the hosted URL, and choose no authentication. This exercises the server tools; use the local marketplace to test the packaged instructions as well. Account and workspace permissions determine which installation options are available.

## Prepare public submission

A Git-backed marketplace provides local or team distribution. Anthropic's plugin directory and OpenAI's public directory each require a separate submission and review.

### Anthropic plugin directory

`claude plugin validate plugin --profile directory` runs the subset of the directory's checks that the CLI can run locally: README length, icon, privacy policy and support URLs. The Claude manifest already carries `privacyPolicyUrl`, `termsOfServiceUrl`, `supportUrl`, `documentationUrl` and the Duke icon. The directory also runs a safety scan and a policy review that the CLI does not.

### OpenAI directory

The ZIP can be built as a draft now. Before requesting review, add real, publicly accessible values in [plugin/plugin.json](../plugin/plugin.json):

| Location under `extensions.com.openai` | Required value |
| --- | --- |
| `interface.privacyPolicyURL` | Public URL of the hosted service's [privacy notice](../PRIVACY.md), after publishing it |
| `interface.termsOfServiceURL` | Public URL of the hosted service's [terms](../TERMS.md), after publishing them |
| `review.demo_recording_url` | Reviewer-accessible video walkthrough |

Policy text should reflect actual hosting, logging and upstream data handling. The repository's BSD software license is included in the package; it does not supply these service-policy fields. The existing website and support links identify this project and its maintainer. Check that the selected verified developer identity matches the listing.

The demo is a video walkthrough available to reviewers, not a separate document or a file that must be included in the ZIP. Record the installed plugin answering representative requests, including tool use, results and source links, then set `extensions.com.openai.review.demo_recording_url` in [plugin/plugin.json](../plugin/plugin.json) to the hosted video's HTTPS URL. Reviewers must be able to open it. The existing five positive and three negative cases can guide the recording; they remain separate review metadata. The [submission guide](https://developers.openai.com/plugins/deploy/submission) requires the video URL for MCP review.

Run the stricter local check after filling these fields:

```sh
node plugin-tools/pack.mjs --submission
```

This checks the required local metadata, including exactly five positive and three negative review cases. It does not verify URL availability, domain ownership, portal scans or approval.

In the [Plugins dashboard](https://platform.openai.com/plugins), select the owning organization/project and verified developer identity, then upload the ZIP using **With MCP**. The MCP connection is already included in the initial package. Connect the server with no authentication, complete the portal's domain challenge, resolve metadata/skill/tool findings, and submit for review. Publish after approval. See the [submission guide](https://developers.openai.com/plugins/deploy/submission) for account permissions and the current process.

The domain challenge belongs at the HTTPS origin shown by the portal, at `/.well-known/openai-apps-challenge`, serving its exact token. Coordinate this with the `api.hirt.se` hosting configuration; the `/openjdk` application prefix does not determine that challenge URL.

Keep subsequent metadata or skill edits in this repository and upload a newly versioned ZIP. Hosted MCP tool updates are scanned separately by OpenAI after deployment. Building or attaching this ZIP to a GitHub release does not publish it in the ChatGPT/Codex directory.
