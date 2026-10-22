# Tools and workflows

[Documentation](README.md) · [Installation](../README.md#installing)


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

## Contributor and project-lead workflows

Start with `getContributionContext(owner="openjdk", repo="jdk17u", ref="master")`: the census project comes from the repository configuration (`jdk-updates` in this example), rather than an assumption that its name equals the repository name. `getProjectMembers` supports comma-separated roles, such as `lead,reviewer`, with `limit` (1–100) and `offset` (0–10000). Roles identify membership, not subject expertise or availability to review.

Use `getOpenJdkReview` with an owner, repository and PR number. It preserves the complete PR body, including Skara sections, and marks reviews against the current head. The Skara Reviewers section is parsed into `skaraReviewers` (name, OpenJDK ID, GitHub login, role, re-review flag) and each GitHub review carries the matching `skaraRole`; Skara Error and Warning sections become `skaraBlockers` and `skaraWarnings`. Missing or truncated checks produce a partial result. It reports evidence, leaving integration approval to Skara and project policy.

Skara never merges through GitHub: an integrated change is a closed PR with the `integrated` label, and GitHub's `merged` flag stays false. Every tool reports `integrated` from that label. `getPullRequestTriage(owner, repo)` scans up to three pages of open PRs and groups them as `readyToIntegrate`, `awaitingReview`, `blockedByErrors`, `csrPending`, `mergeConflict`, `stale`, `drafts` and `unlabeled`, with counts and sample items. `getContributorRecord(owner, repo, login)` counts a GitHub user's integrated PRs with the earliest and latest examples, plus open and closed-but-not-integrated counts, for nominations. `getProjectActivity(owner, repo, since, until)` summarizes a reporting period: integrated PRs, authors ranked by integrations, opened PRs and first-time contributors (checked for the ten most active authors). These use GitHub search, which allows ten anonymous requests per minute; configure a token for routine use.

`getIssueFixStatus(key)` answers "where is this fixed?": it reads the main issue and runs one JQL query for linked Backport issues, returning each backport's fix version, status and resolution, and the `fixedIn` and `pendingIn` release lists. `searchIssues` accepts exact `type`, `fixVersion`, `component`, `priority`, `assignee` (or `unassigned`), `resolution` (or `unresolved`), `updatedSince` and `resolvedSince` filters and returns the generated JQL. `getReleaseSchedule(26)` reads the official release page: milestones with dates, the phase reached today, the next milestone, General Availability and targeted JEPs. `searchMailingList(list="jmc-dev", month="2026-02", query="CFV")` lists one month of a pipermail archive with thread depth and subject classification; omit `month` for the newest archived month (the archives can lag the calendar). Message bodies are not fetched. `searchDevelopersGuide(query="sponsor")` returns verbatim, bounded excerpts of matching guide sections with links.

`getBackportStatus` takes an original commit (7–40 hexadecimal characters) and up to five targets, for example `openjdk/jdk21u,openjdk/jdk17u@master`. It verifies original-commit ancestry and `Backport-of` provenance against the target branch, or reports matching PR evidence. Results distinguish `found`, `not_found_in_search`, `partial` and `unknown`. Searches are bounded; absence from results does not establish absence of a backport, and a later revert may change its effect.

`getBuildGuidance` accepts `ref`, optional `topic`, and an optional test path. It returns source excerpts and line numbers from `doc/building.md`, `doc/testing.md`, and selected ProblemList files. Missing documents are explicit. A missing ProblemList match does not establish that a test is enabled on every platform.

`getProjectProcesses(projectid="jmc", topic="committer")` gives steps, applicability and authoritative links. Omit `topic` for all ten processes. The catalog records when its summaries were reviewed; project-specific requirements and linked governing documents remain authoritative. It includes the distinction between Committer Lazy Consensus and Reviewer Three-Vote Consensus, plus quarterly reporting and succession responsibilities.

## REST API

The Custom GPT Actions schema is in [examples/customgpt.txt](../examples/customgpt.txt). Existing REST paths and comma-separated ID queries remain supported.

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
