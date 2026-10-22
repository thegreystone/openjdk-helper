# Configuration

[Documentation](README.md) · [Installation](../README.md#installing)

Use these settings for local and self-hosted installations. Configuration properties can be supplied through system properties or their environment-variable equivalents; defaults are in [application.properties](../src/main/resources/application.properties).

## Transports

HTTP mode serves REST/OpenAPI and MCP on port 8080 by default, with the local MCP endpoint at `http://localhost:8080/mcp`. The HTTP listener binds to all network interfaces unless configured otherwise.

Start a local server with `-Dquarkus.profile=stdio` to speak MCP on stdin/stdout and disable the HTTP listener. Console logs go to stderr so stdout stays reserved for JSON-RPC. The older `-Dquarkus.mcp.server.stdio.enabled=true` switch enables stdio while leaving the HTTP listener enabled; use the profile for desktop clients.

See [development](development.md#running-a-build-you-made-yourself-over-stdio) for running a build over stdio, and [deployment](deployment.md) for containers, reverse proxies and multiple replicas.

## Upstream configuration and availability

| Property | Default / purpose |
| --- | --- |
| `openjdk.census.url` | `https://openjdk.org/census.xml`; refreshes hourly, retains the last valid snapshot after errors |
| `openjdk.github.api-base-url` | `https://api.github.com` |
| `openjdk.github.raw-base-url` | `https://raw.githubusercontent.com` |
| `openjdk.github.token` | Optional bearer token; environment variable `OPENJDK_GITHUB_TOKEN` |
| `openjdk.jbs.base-url` | `https://bugs.openjdk.org` |
| `openjdk.jbs.token` | Optional bearer token accepted by the configured Jira instance; `OPENJDK_JBS_TOKEN` |
| `openjdk.jeps.base-url` | `https://openjdk.org` |
| `openjdk.site.base-url` | `https://openjdk.org`; release pages and the Developers' Guide |
| `openjdk.mail.base-url` | `https://mail.openjdk.org`; pipermail archives |
| `openjdk.upstream.timeout-seconds` | 20 seconds per request, including body delivery; maximum 60 |
| `openjdk.upstream.cache-seconds` | 120 seconds; 0 disables reuse |

GitHub, JBS, JEP and repository-document requests share a bounded cache and a 2 MiB response limit. Responses retain source URLs and fetch times. GitHub rate-limit responses include retry advice when provided by GitHub. A token can increase GitHub's public API allowance; it is never included in results.

JBS access depends on the upstream deployment. Access denial returns a clear unavailable response (REST 503, MCP tool error) with the upstream status and source link. Authentication cannot guarantee access. JBS search accepts literal text and exact filters for the JDK, JMC, CODETOOLS and SKARA projects, rather than unrestricted JQL. JEP, release schedule, mailing list and Developers' Guide data are read from the published HTML; unexpected page formats produce an explicit error. Redirects are never followed, so a moved resource is reported with its new location instead of being fetched with the caller's token.
