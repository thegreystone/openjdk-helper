# ChatGPT and Codex plugin

The [plugin source directory](../plugin/) contains a portable Agent Plugin. It pairs OpenJDK instructions with the hosted MCP server at `https://api.hirt.se/openjdk/mcp`. No Java runtime or local server is needed by plugin users. The existing [MCPB bundles](../mcpb/) provide the native local stdio server; REST/OpenAPI remains available for Custom GPT Actions and other clients.

The package follows OpenAI's [package format](https://developers.openai.com/plugins/build/plugins) and [submission guide](https://developers.openai.com/plugins/deploy/submission), checked on 2026-10-21.

## Build

Use Node.js 22 or later. Run all commands in this guide from the repository root:

```sh
npm ci --prefix plugin --ignore-scripts
node --test plugin/test-plugin.mjs
node plugin/pack.mjs
```

The output is `target/openjdk-helper-<version>-plugin.zip`. The version comes from [pom.xml](../pom.xml); `node plugin/pack.mjs --version 0.2.1` overrides it for a release without changing source files. Packaging does not build or deploy the Java service. CI validates and builds the package, and the release workflow attaches the ZIP to future GitHub releases.

The archive contains:

```text
plugin.json
mcp.json
skills/openjdk-helper/SKILL.md
assets/duke-icon.png
LICENSE
```

Edit [plugin/plugin.json](../plugin/plugin.json) for listing and review information, [plugin/mcp.json](../plugin/mcp.json) for the server connection, and [plugin/skills/openjdk-helper/SKILL.md](../plugin/skills/openjdk-helper/SKILL.md) for assistant behavior. These are new instructions based on this service's capabilities; an export of the existing Custom GPT instructions was not available in the repository. Additional GPT-specific guidance can go in the skill. To include separate knowledge files, extend the [packager's explicit file list](../plugin/pack.mjs) and its [archive test](../plugin/test-plugin.mjs).

The packager checks this project's expected structure, metadata and safe archive paths. It is not a complete implementation of the official JSON schemas or a substitute for the portal's checks.

## Test the complete plugin

Copy [plugin/marketplace.json](../plugin/marketplace.json) to `.agents/plugins/marketplace.json` at the repository root, merging its entry if you already have a marketplace. Its `./plugin` source path is relative to the repository root. Install from that local marketplace in the Plugins Directory, then enable it in a fresh conversation. Reinstall after editing: local installs use a cached copy.

Run the prompts in [plugin/plugin.json](../plugin/plugin.json) under `extensions.com.openai.review.test_cases`, and record tool selection, arguments, results and source links. Confirm that the skill handles partial upstream data and that unsupported requests cause no writes. The positive cases describe expected behavior; they are not a record of successful end-to-end runs. See OpenAI's [connection and evaluation guide](https://developers.openai.com/plugins/deploy/connect-chatgpt).

To test just the server in ChatGPT, use Plugins → Add custom MCP server, enter the hosted URL, and choose no authentication. This exercises the server tools; use the local marketplace to test the packaged instructions as well. Account and workspace permissions determine which installation options are available.

## Sharing a repository with a Claude marketplace

The [claude-plugins repository](https://github.com/thegreystone/claude-plugins) can also host an OpenAI marketplace catalog. Keep the existing Claude catalog at `.claude-plugin/marketplace.json` and add an OpenAI catalog at `.agents/plugins/marketplace.json` in that same repository. OpenAI also supports the Claude catalog location as a legacy-compatible option; separate files make each client's metadata easier to maintain. See the [OpenAI marketplace documentation](https://developers.openai.com/plugins/build/plugins#how-local-marketplaces-work).

The OpenAI catalog can point directly to this repository's plugin folder:

```json
{
  "name": "hirt-openai-plugins",
  "plugins": [
    {
      "name": "openjdk-helper",
      "source": {
        "source": "git-subdir",
        "url": "https://github.com/thegreystone/openjdk-helper.git",
        "path": "./plugin",
        "ref": "main"
      },
      "policy": {
        "installation": "AVAILABLE",
        "authentication": "ON_INSTALL"
      },
      "category": "Developer Tools"
    }
  ]
}
```

Register that marketplace with:

```sh
codex plugin marketplace add thegreystone/claude-plugins
```

Claude and OpenAI use different plugin manifests and MCP configuration conventions. This portable package's `plugin.json` and `mcp.json` do not by themselves make a Claude plugin; shared skills can be reused with the appropriate host configuration. See the [Claude manifest reference](https://code.claude.com/docs/en/plugins/manifest-reference).

A Git-backed marketplace provides local or team distribution. OpenAI's public directory requires the submission and review described below.

## Prepare public submission

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
node plugin/pack.mjs --submission
```

This checks the required local metadata, including exactly five positive and three negative review cases. It does not verify URL availability, domain ownership, portal scans or approval.

In the [Plugins dashboard](https://platform.openai.com/plugins), select the owning organization/project and verified developer identity, then upload the ZIP using **With MCP**. The MCP connection is already included in the initial package. Connect the server with no authentication, complete the portal's domain challenge, resolve metadata/skill/tool findings, and submit for review. Publish after approval. See the [submission guide](https://developers.openai.com/plugins/deploy/submission) for account permissions and the current process.

The domain challenge belongs at the HTTPS origin shown by the portal, at `/.well-known/openai-apps-challenge`, serving its exact token. Coordinate this with the `api.hirt.se` hosting configuration; the `/openjdk` application prefix does not determine that challenge URL.

Keep subsequent metadata or skill edits in this repository and upload a newly versioned ZIP. Hosted MCP tool updates are scanned separately by OpenAI after deployment. Building or attaching this ZIP to a GitHub release does not publish it in the ChatGPT/Codex directory.
