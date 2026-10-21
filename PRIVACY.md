# OpenJDK Helper privacy notice

Last updated: 21 October 2026

## Scope and operator

Marcus Hirt operates the OpenJDK Helper service at `https://api.hirt.se/openjdk`. This notice covers its hosted REST/OpenAPI and MCP endpoints, including access through the OpenJDK Project Assistant Custom GPT and OpenJDK Helper plugin. It does not cover unrelated pages on hirt.se.

The service provides read-only information for OpenJDK contributors and project leads. No service account or sign-in is required. Local installations are described separately below.

## Information processed and its purpose

- **Requests:** tool names and arguments, URL parameters and protocol information needed to answer a request. Depending on the tool, inputs can include names, OpenJDK or GitHub usernames, repository names, issue and pull-request identifiers, commit hashes, dates and search terms. The tool interfaces do not request your full conversation history; any information your client includes in a tool argument is received by the service.
- **Retrieved information:** source material used to answer requests, including public contributor names, usernames, affiliations, roles, contribution records and, where present in upstream data, email addresses or other personal information. Responses and cached source material may contain this information.
- **Connection and diagnostic information:** connecting IP addresses, timestamps, request URLs, response status and size, referrers, user-agent information and error details. This information supports service operation, troubleshooting and protection against abuse. The proxy also uses connecting IP addresses to limit request rates. A connecting IP address may belong to your AI provider or another client intermediary rather than your own device.
- **Privacy enquiries:** contact details and information you provide when contacting the maintainer, used to handle your enquiry and necessary follow-up.

Please keep requests relevant to OpenJDK work and avoid including confidential information, passwords, access tokens or other sensitive personal information. Hosted tools do not ask you to provide credentials. The API does not use advertising or tracking cookies.

## Other services and recipients

The helper retrieves information from GitHub, including its API and raw repository content, and OpenJDK services at `openjdk.org`, `bugs.openjdk.org` and `mail.openjdk.org`. Relevant identifiers or search filters may become part of those outbound requests; some searches are performed locally over retrieved content. These services receive the request and the helper server's network address. Operator-configured upstream credentials, if used, are sent to the relevant upstream service.

Results are returned to the client that requested them. When you use an AI assistant, its provider receives those results and handles your conversation under its own privacy policy. GitHub and OpenJDK services likewise apply their own policies to requests they receive.

The service operator can access operational logs for the purposes described above. The helper does not sell request data or use it for advertising or model training.

## Storage and retention

The application does not maintain user accounts, a conversation-history database or saved user profiles. Stateless tool operations still involve processing, caches and infrastructure logs:

| Data | Storage and retention |
| --- | --- |
| Upstream responses and request-derived cache keys | Held in application memory. The shared response cache has size and entry limits. Its default 120-second freshness window controls reuse, not deletion: entries can remain until replacement, eviction or process termination. |
| Census and other source snapshots | Held in application memory and refreshed or replaced as needed. Older data may be retained in memory when a refresh fails, until a later replacement or process termination. |
| nginx access and error logs | Stored on the proxy host. Rotation is scheduled daily for nonempty files, retaining up to 14 archives per log plus the active file. Empty logs skip rotation, so older archives, particularly error logs, can remain for months. This is not a fixed deletion deadline. |
| Application diagnostic logs | Captured from the containers on Kubernetes nodes. Logs are configured to rotate at approximately 10 MiB per file, retaining up to five files per container. Rotation is checked every ten seconds, so temporary size overruns are possible. Retention depends on log volume and container lifecycle, with no configured maximum age in days. |
| Proxy buffering | nginx may temporarily write request or response content to disk while handling traffic. These buffers are separate from application caches and access logs. |
| Privacy correspondence | Retained for as long as needed to handle the enquiry, necessary follow-up and any applicable recordkeeping obligations. |

The hosted service's nginx and Kubernetes application logs are not backed up or forwarded to external log collection services.

The configured nginx access-log format includes request URLs and their query parameters, but not POST request bodies. Application MCP traffic logging is disabled in the supplied configuration. Diagnostic errors may nevertheless include request-related information; this notice does not promise that every input is absent from all logs.

## Your choices and privacy requests

You can stop using the service or disconnect the plugin or Custom GPT action in your client. The hosted service has no per-user switch for operational logging. Running the local stdio server avoids sending tool requests through the hirt.se service, while still involving your chosen AI client and the relevant upstream services.

Depending on applicable law, you may request access to, correction or deletion of personal data, object to processing or request a restriction, and complain to a competent data-protection authority. Contact the maintainer privately using the address below. Include only the information needed to explain your request. Because the service has no user accounts and some clients share network addresses, it may not be possible to associate a log entry with a particular person without further information.

For inaccurate information originating in a public OpenJDK or GitHub record, contact the source maintainer as well. The helper cannot change upstream records or delete copies held by your AI provider or other independent services.

## Local and self-hosted installations

When you run the native executable or MCPB bundle locally over stdio, the helper connects to upstream services from your machine. Its tool requests do not pass through hirt.se. Memory caches exist in that local process, and diagnostic output goes to stderr, which your client may retain. Optional upstream tokens are configured locally and used for requests to the corresponding upstream service; they are not tool arguments.

Logging, retention and access controls for local or independently hosted installations depend on their operator and client configuration. The hosted nginx and Kubernetes retention settings above do not apply to those installations.

## Contact and changes

Operator: Marcus Hirt.

Privacy contact: [mcp@hirt.se](mailto:mcp@hirt.se).

The [project issue tracker](https://github.com/thegreystone/openjdk-helper/issues) can be used for non-sensitive project questions. Issues are public; do not post personal records, credentials or private privacy requests there.

This notice will be updated when the service's data handling changes. The date at the top identifies the latest revision.
