# OpenJDK Helper plugin

Read-only tools for OpenJDK contributors and project leads: census roles, pull requests, issues and backports, JEPs, release schedules and project processes. The plugin pairs the `openjdk-helper` skill with the free hosted MCP service at `https://api.hirt.se/openjdk/mcp`. It needs no account, Java runtime or local server. All tools are read-only; the plugin never posts reviews, integrates changes, updates issues or sends mail.

This directory is one plugin source for two plugin formats. Claude Code and Claude Desktop read `.claude-plugin/plugin.json` and `.mcp.json`; ChatGPT and Codex read `plugin.json` and `mcp.json`. Both share `skills/` and `assets/`.

Install in Claude Code from the [AI Plugins marketplace](https://github.com/thegreystone/ai-plugins):

```
/plugin install openjdk-helper --marketplace thegreystone/ai-plugins
```

Codex users add the same marketplace with `codex plugin marketplace add thegreystone/ai-plugins`. See the [plugin development guide](../docs/plugin.md) for local testing, packaging and public submission, the [privacy notice](../PRIVACY.md) and the [service terms](../TERMS.md).
