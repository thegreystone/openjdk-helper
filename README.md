[![Build](https://github.com/thegreystone/openjdk-helper/actions/workflows/build.yml/badge.svg)](https://github.com/thegreystone/openjdk-helper/actions/workflows/build.yml)
[![Java](https://img.shields.io/badge/Java-25%2B-blue)](https://openjdk.org/projects/jdk/25/)
[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg)](LICENSE)

# OpenJDK Helper

A Quarkus service for OpenJDK contributors and project leads: census roles, GitHub reviews and backports, JBS issues, JEPs, branch-specific build guidance and project processes. It serves REST/OpenAPI for the [OpenJDK Project Assistant](https://chatgpt.com/g/g-cdK5pudqC-openjdk-project-assistant) and exposes the same operations through MCP.

## Public service

The REST service is deployed at `https://api.hirt.se/openjdk`:

- [Version](https://api.hirt.se/openjdk/version)
- [Swagger UI](https://api.hirt.se/openjdk/swagger-ui/)
- [OpenAPI schema](https://api.hirt.se/openjdk/openapi/)
- [Example census lookup](https://api.hirt.se/openjdk/census/people?userid=hirt)

The Custom GPT Actions schema is in [examples/customgpt.txt](examples/customgpt.txt). Existing REST paths and comma-separated ID queries remain supported.

## MCP

The server uses the Quarkiverse MCP extension for protocol negotiation, sessions and tool discovery. All 27 tools are read-only:

| Tool | Purpose |
| --- | --- |
| `getVersion` | Service version |
| `searchPeople` / `getPerson` | Find census people; `getPerson` accepts comma-separated IDs |
| `searchProjects` / `getProject` | Find census projects |
| `searchGroups` / `getGroup` | Find census groups |
| `getProjectMembers` / `getCensusStatus` | Filter members by exact census role and literal name; inspect snapshot freshness |
| `getGitHubRepos` | Page through repositories for a GitHub user or organization |
| `getGitHubPulls` | Page through PRs, filtering author/state and choosing sort/direction |
| `getOpenJdkReview` | PR body and Skara progress, reviewers with census roles, blockers, JBS links, reviews and checks at the observed head SHA |
| `getBackportStatus` | Search original-commit/backport provenance for explicit target repositories and branches |
| `getPullRequestTriage` | Group a repository's open PRs by Skara state: ready, awaiting review, blocked, CSR, conflicts, stale, drafts |
| `getContributorRecord` | Integrated, open and abandoned PR counts for one GitHub user, with earliest and latest examples |
| `getProjectActivity` | Integrated PRs, ranked authors, opened PRs and first-time contributors in a period |
| `getContributionContext` | Resolve census project, JBS project and declared review rules from `.jcheck/conf` |
| `getBuildGuidance` | Read build/testing excerpts and matching ProblemList entries from the selected ref |
| `getIssue` / `searchIssues` | JBS issues, versions and directed CSR/backport links; literal text plus exact type, version, component, priority, assignee, resolution and date filters |
| `getIssueFixStatus` | The main issue and every linked Backport issue with fix versions, summarized as `fixedIn` and `pendingIn` |
| `getJep` / `searchJeps` | Published JEP metadata, summaries and links; filter by title, status and release |
| `getReleaseSchedule` | JDK release milestones, the phase reached today, the next milestone and targeted JEPs |
| `searchMailingList` | One month of a mail.openjdk.org archive: subjects, authors, thread depth, with CFV/Result/RFR classification |
| `searchDevelopersGuide` | Verbatim section excerpts from the OpenJDK Developers' Guide, or its table of contents |
| `getProjectProcesses` | Lead duties, appointments, elections, voting, succession, review, CSR, JEP and backport processes |

The legacy census search tools use case-insensitive regular expressions. Member, issue, JEP and process filters use literal text. Data tools return JSON text; failed operations and invalid arguments return MCP tool errors. Unknown tools return protocol errors.

### Contributor and project-lead workflows

Start with `getContributionContext(owner="openjdk", repo="jdk17u", ref="master")`: the census project comes from the repository configuration (`jdk-updates` in this example), rather than an assumption that its name equals the repository name. `getProjectMembers` supports comma-separated roles, such as `lead,reviewer`, with `limit` (1–100) and `offset` (0–10000). Roles identify membership, not subject expertise or availability to review.

Use `getOpenJdkReview` with an owner, repository and PR number. It preserves the complete PR body, including Skara sections, and marks reviews against the current head. The Skara Reviewers section is parsed into `skaraReviewers` (name, OpenJDK ID, GitHub login, role, re-review flag) and each GitHub review carries the matching `skaraRole`; Skara Error and Warning sections become `skaraBlockers` and `skaraWarnings`. Missing or truncated checks produce a partial result. It reports evidence, leaving integration approval to Skara and project policy.

Skara never merges through GitHub: an integrated change is a closed PR with the `integrated` label, and GitHub's `merged` flag stays false. Every tool reports `integrated` from that label. `getPullRequestTriage(owner, repo)` scans up to three pages of open PRs and groups them as `readyToIntegrate`, `awaitingReview`, `blockedByErrors`, `csrPending`, `mergeConflict`, `stale`, `drafts` and `unlabeled`, with counts and sample items. `getContributorRecord(owner, repo, login)` counts a GitHub user's integrated PRs with the earliest and latest examples, plus open and closed-but-not-integrated counts, for nominations. `getProjectActivity(owner, repo, since, until)` summarizes a reporting period: integrated PRs, authors ranked by integrations, opened PRs and first-time contributors (checked for the ten most active authors). These use GitHub search, which allows ten anonymous requests per minute; configure a token for routine use.

`getIssueFixStatus(key)` answers "where is this fixed?": it reads the main issue and runs one JQL query for linked Backport issues, returning each backport's fix version, status and resolution, and the `fixedIn` and `pendingIn` release lists. `searchIssues` accepts exact `type`, `fixVersion`, `component`, `priority`, `assignee` (or `unassigned`), `resolution` (or `unresolved`), `updatedSince` and `resolvedSince` filters and returns the generated JQL. `getReleaseSchedule(26)` reads the official release page: milestones with dates, the phase reached today, the next milestone, General Availability and targeted JEPs. `searchMailingList(list="jmc-dev", month="2026-02", query="CFV")` lists one month of a pipermail archive with thread depth and subject classification; omit `month` for the newest archived month (the archives can lag the calendar). Message bodies are not fetched. `searchDevelopersGuide(query="sponsor")` returns verbatim, bounded excerpts of matching guide sections with links.

`getBackportStatus` takes an original commit (7–40 hexadecimal characters) and up to five targets, for example `openjdk/jdk21u,openjdk/jdk17u@master`. It verifies original-commit ancestry and `Backport-of` provenance against the target branch, or reports matching PR evidence. Results distinguish `found`, `not_found_in_search`, `partial` and `unknown`. Searches are bounded; absence from results does not establish absence of a backport, and a later revert may change its effect.

`getBuildGuidance` accepts `ref`, optional `topic`, and an optional test path. It returns source excerpts and line numbers from `doc/building.md`, `doc/testing.md`, and selected ProblemList files. Missing documents are explicit. A missing ProblemList match does not establish that a test is enabled on every platform.

`getProjectProcesses(projectid="jmc", topic="committer")` gives steps, applicability and authoritative links. Omit `topic` for all ten processes. The catalog records when its summaries were reviewed; project-specific requirements and linked governing documents remain authoritative. It includes the distinction between Committer Lazy Consensus and Reviewer Three-Vote Consensus, plus quarterly reporting and succession responsibilities.

REST examples (all also appear in `/openapi`):

```text
GET /census/projects/jmc/members?roles=lead,reviewer&limit=30
GET /census/status
GET /github/pulls?owner=openjdk&repo=jmc&state=all&maxresults=30&page=1
GET /github/review?owner=openjdk&repo=jmc&number=737
GET /github/backports?owner=openjdk&repo=jdk&commit=<sha>&targets=openjdk/jdk21u
GET /github/triage?owner=openjdk&repo=jmc&staleDays=30
GET /github/contributor?owner=openjdk&repo=jmc&login=thegreystone
GET /github/activity?owner=openjdk&repo=jmc&since=2026-07-01&until=2026-09-30
GET /contribution/context?owner=openjdk&repo=jdk17u&ref=master
GET /contribution/build-guidance?owner=openjdk&repo=jdk&ref=master&topic=jtreg
GET /issues/JMC-8639
GET /issues/JDK-8394772/fix-status
GET /issues/search?project=JDK&type=CSR&fixVersion=26&resolution=unresolved&limit=20
GET /jeps/517
GET /jeps/search?status=Delivered&release=25&limit=20
GET /releases/jdk/26
GET /mail/threads?list=jmc-dev&month=2026-02&query=CFV
GET /guide/search?query=backport
GET /processes?projectid=jmc&topic=reviewer
```

Existing GitHub REST list endpoints still return arrays. Pagination is exposed through `X-Page`, `X-Page-Size`, `X-Has-More` and `X-Next-Page`; MCP returns an envelope with `items`, `page`, `hasMore`, `nextPage` and source metadata. PR author filtering applies within the upstream page, so follow `nextPage` even when that page has no matching authors. Default page size is 30, maximum 100; the REST `maxresults=-1` option now selects a bounded 100-item page.

### Upstream configuration and availability

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

### Remote HTTP

Use **Streamable HTTP** with `http://localhost:8080/mcp` locally. After deploying this build to the public service, the corresponding URL is `https://api.hirt.se/openjdk/mcp`. Legacy SSE clients can use `/mcp/sse`. `/mcp-info` serves human-readable connection information.

For an MCP client that accepts a remote URL:

```json
{
  "mcpServers": {
    "openjdk-helper": {
      "url": "https://api.hirt.se/openjdk/mcp"
    }
  }
}
```

Select Streamable HTTP in the client's transport settings. The existing production deployment may need upgrading before it supports this endpoint.

### Install a local MCPB bundle

Download `openjdk-helper-X.Y.Z-<platform>.mcpb` from [GitHub Releases](https://github.com/thegreystone/openjdk-helper/releases) and install it using your MCP client's extension installer. Choose the package matching your machine: `windows-x86_64`, `macos-aarch64` (Apple Silicon), `linux-x86_64`, or `linux-aarch64`.

Each bundle contains a native executable and launches with the `stdio` profile by default. It needs no Java installation or port configuration. The HTTP listener is disabled; the local process fetches OpenJDK, GitHub and JBS data as needed. Public sources work without credentials where permitted; optional tokens and JBS access constraints are described above.

To build a bundle yourself after compiling the native executable, install Node.js 22 or later for the packaging tools:

```sh
npm ci --prefix mcpb
node mcpb/pack.mjs 0.2.0-SNAPSHOT windows-x86_64 target/openjdk-helper-0.2.0-SNAPSHOT-runner.exe
node mcpb/test-mcpb.mjs target/openjdk-helper-0.2.0-SNAPSHOT-windows-x86_64.mcpb
```

Use the matching suffix and binary for Linux/macOS. Packaging validates the manifest and executable architecture. The smoke test extracts the finished archive and launches its manifest command to check MCP initialization, tool discovery and census lookup, clean stdout, and disabled HTTP binding. Node.js is only needed to build/test bundles, not to use them.

### Signing release binaries

The release workflow signs the macOS binary with a Developer ID certificate (hardened runtime, timestamp) and notarizes it before packing the bundle, using the `MACOS_CERTIFICATE`, `MACOS_CERTIFICATE_PWD`, `MACOS_SIGNING_IDENTITY`, `NOTARIZATION_APPLE_ID`, `NOTARIZATION_PASSWORD` and `NOTARIZATION_TEAM_ID` repository secrets. A bare executable cannot be stapled, so Gatekeeper verifies the notarization ticket online on first launch.

The Windows binary is published unsigned and signed afterwards with a Certum code-signing certificate through SimplySign Desktop, which needs an interactive OTP login and therefore runs on the maintainer's machine. With SimplySign Desktop logged in, `gh` authenticated and `npm ci --prefix mcpb` done:

```powershell
.\mcpb\Sign-Release.ps1 -Version 0.2.0            # add -NoUpload to inspect target\signed first
```

The script downloads the Windows binary from the release, signs it (SHA-256, Certum RFC 3161 timestamp), verifies signature and timestamp, re-packs the Windows `.mcpb` around the signed binary with `mcpb/pack.mjs`, runs `mcpb/test-mcpb.mjs` against that bundle and replaces both release assets. The certificate thumbprint comes from `$env:CERTUM_SIGN_THUMBPRINT` (or `$env:DISKSPACE_SIGN_THUMBPRINT`), so renewal is a profile edit rather than a commit; always pin by thumbprint. Not signed: the `.mcpb` container itself (its signing needs a PEM private key that neither SimplySign nor the Apple keychain exposes) and the Linux binaries.

### Manual local stdio

The `stdio` profile enables MCP over stdin/stdout and disables the HTTP listener, so multiple desktop clients can launch their own process without port conflicts. Logs go to stderr.

Build a jar with Java 25 or later:

```sh
./mvnw verify -Duber-jar -DskipITs=false
java -Dquarkus.profile=stdio -jar target/openjdk-helper-0.2.0-SNAPSHOT-runner.jar
```

For a native executable, use the same profile:

```sh
./target/openjdk-helper-0.2.0-SNAPSHOT-runner -Dquarkus.profile=stdio
```

Example desktop MCP configuration (substitute the absolute path to your binary):

```json
{
  "mcpServers": {
    "openjdk-helper": {
      "command": "C:\\tools\\openjdk-helper\\openjdk-helper-0.2.0-SNAPSHOT-runner.exe",
      "args": ["-Dquarkus.profile=stdio"]
    }
  }
}
```

For the jar, use `java` as the command and arguments `-Dquarkus.profile=stdio`, `-jar`, and the absolute jar path. If a Windows client starts the process in a restricted directory, set its working directory to a writable directory, or supply `-Duser.dir=C:\\tools\\openjdk-helper`.

The older `-Dquarkus.mcp.server.stdio.enabled=true` switch still works; it leaves the HTTP listener enabled. Prefer the profile for desktop clients.

## Build and test

Requirements: JDK 25 or later. Use `mvnw.cmd` in PowerShell/Command Prompt instead of `./mvnw`.

```sh
# REST, parsing and MCP tests, followed by packaged JVM protocol checks
./mvnw verify -DskipITs=false

# Development mode
./mvnw quarkus:dev

# Run the default fast-jar package (keep the whole quarkus-app directory)
java -jar target/quarkus-app/quarkus-run.jar
```

Tests use local census XML, GitHub, JBS, JEP, release-page, mailing-list, Developers' Guide and repository-document fixtures, independent of upstream availability and membership changes. The older census HTML parser retains compatibility tests. Packaged JVM and native tests exercise REST/OpenAPI and all 27 tools over HTTP and stdio, including error handling and non-JSON stdout rejection. The MCPB test also verifies that process summaries are present in the installed native artifact.

### Native executable

Install GraalVM for JDK 25 and the platform C/C++ toolchain. On Windows, use Visual Studio C++ Build Tools with the Windows SDK (or its x64 Native Tools command prompt). Set `JAVA_HOME` to GraalVM and put its `bin` directory on `PATH`.

```sh
./mvnw verify -Dnative
```

This builds `target/openjdk-helper-0.2.0-SNAPSHOT-runner` (`.exe` on Windows) and runs the packaged protocol checks against that executable. No JVM is needed to run it. Unused AWT/image-processing dependencies have been removed, so no font libraries or AWT sidecars are required.

Alternatively, build a Linux binary with Docker/Podman:

```sh
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

Run native `verify` on a matching host platform; a container-built Linux binary cannot execute directly on Windows or macOS.

### Containers and reverse proxy

Container creation is opt-in:

```sh
./mvnw package -Dquarkus.container-image.build=true
# Linux native container
./mvnw package -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true

docker run --rm -p 8080:8080 greystone/openjdk-helper:latest
```

Pushing a `v*` tag publishes the multi-architecture JVM image as `greystone/openjdk-helper:X.Y.Z` and `:latest` through `.github/workflows/release.yml`, using the `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` repository secrets. `:latest` is no longer pushed on every commit to `main`. To publish an image by hand, for example a hotfix without a release:

```sh
./mvnw clean package -DskipTests
docker buildx build -f src/main/docker/Dockerfile.jvm --platform linux/amd64,linux/arm64 \
  -t greystone/openjdk-helper:X.Y.Z -t greystone/openjdk-helper:latest --push .
```

For hirt.se, forward `/openjdk/` to the application's `/`, preserve the request method, and pass `MCP-Session-Id` and `MCP-Protocol-Version` headers. Disable response buffering and allow long-lived connections for MCP event streams. Forwarded host/prefix support is enabled; supply these headers only from the trusted reverse proxy.

Streamable HTTP uses the single `/openjdk/mcp` URL. With the legacy SSE transport, this extension advertises `/mcp/messages/{id}` in its first event; a proxy that strips `/openjdk` must also route that advertised message path to this service. Prefer Streamable HTTP for a deployment under a path prefix.

## Releases

[GitHub Releases](https://github.com/thegreystone/openjdk-helper/releases) are produced by pushing a `vX.Y.Z` tag. The workflow builds and tests an uber-jar, native executables, and MCPB bundles for Linux x86_64/aarch64, macOS aarch64 and Windows x86_64, then publishes the release artifacts and a multi-architecture JVM Docker image (`X.Y.Z` and `latest`). Native and bundle test failures fail the release. Normal builds do not publish images.

Release executables are named `openjdk-helper-X.Y.Z-<platform>` (`.exe` on Windows). The local commands above use the current Maven snapshot name; adjust paths to match a downloaded release.
