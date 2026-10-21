/*
 * Copyright (C) 2026 Marcus Hirt
 *
 * This software is free:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESSED OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package se.hirt.openjdk.helper.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.hirt.openjdk.helper.common.UpstreamException;
import se.hirt.openjdk.helper.common.UpstreamHttp;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real HTTP requests against deterministic GitHub-shaped responses. */
class GitHubServiceTest {
	private static final String ORIGINAL = "a".repeat(40);
	private static final String HEAD = "b".repeat(40);
	private static final String BACKPORT = "c".repeat(40);
	private static final String OLD = "d".repeat(40);
	private final ObjectMapper json = new ObjectMapper();
	private final Map<String, Reply> routes = new ConcurrentHashMap<>();
	/** Search replies keyed by a substring of the decoded query string, checked in insertion order. */
	private final Map<String, Reply> searches = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());
	private final List<URI> requests = new CopyOnWriteArrayList<>();
	private final List<String> authorization = new CopyOnWriteArrayList<>();
	private HttpServer server;
	private UpstreamHttp upstream;
	private GitHubService github;

	@BeforeEach
	void start() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requests.add(exchange.getRequestURI());
			authorization.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
			String path = exchange.getRequestURI().getPath();
			String query = java.net.URLDecoder.decode(String.valueOf(exchange.getRequestURI().getRawQuery()), java.nio.charset.StandardCharsets.UTF_8);
			Reply reply = searches.entrySet().stream().filter(e -> path.equals("/search/issues") && query.contains(e.getKey()))
					.map(Map.Entry::getValue).findFirst()
					.orElse(routes.getOrDefault(path, new Reply(404, Map.of("message", "Not Found"), Map.of())));
			byte[] body = json.writeValueAsBytes(reply.body());
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			reply.headers().forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
			exchange.sendResponseHeaders(reply.status(), body.length);
			try (var output = exchange.getResponseBody()) { output.write(body); }
		});
		server.start();
		upstream = new UpstreamHttp();
		github = new GitHubService(upstream, json, "http://127.0.0.1:" + server.getAddress().getPort(), Optional.of("fixture-token"));
		github.clock(java.time.Clock.fixed(java.time.Instant.parse("2026-10-21T12:00:00Z"), java.time.ZoneOffset.UTC));
	}

	@AfterEach
	void stop() {
		if (upstream != null) upstream.close();
		if (server != null) server.stop(0);
	}

	@Test
	void repositoriesExposePaginationAndCacheMetadataWithoutSilentlyFetchingEverything() {
		routes.put("/users/openjdk/repos", new Reply(200, List.of(repo("jdk"), repo("jmc")),
				Map.of("Link", "<https://api.github.com/users/openjdk/repos?page=2>; rel=\"next\"")));
		JsonNode first = tree(github.listRepositories("openjdk", 2, 1));
		assertEquals(2, first.path("items").size());
		assertTrue(first.path("hasMore").asBoolean());
		assertEquals(2, first.path("nextPage").asInt());
		assertFalse(first.path("source").path("cached").asBoolean());
		assertTrue(requests.get(0).getQuery().contains("per_page=2"));
		JsonNode cached = tree(github.listRepositories("openjdk", 2, 1));
		assertTrue(cached.path("source").path("cached").asBoolean());
		assertEquals(1, requests.size());
		assertEquals("Bearer fixture-token", authorization.get(0));
		try (var response = github.getRepositories("openjdk", 2, 1)) {
			assertInstanceOf(List.class, response.getEntity());
			assertEquals("2", response.getHeaderString("X-Next-Page"));
		}
	}

	@Test
	void pullFiltersAreEncodedDefaultDirectionIsValidAndDescriptionsRemainComplete() {
		String body = "Description\n<!-- a template comment -->\nTests passed\n### Progress\n- [x] Tested\n### Reviewers\nhirt";
		when("/repos/openjdk/jdk/pulls", List.of(pr(1, "duke", body), pr(2, "other", "Other change")));
		JsonNode result = tree(github.listPullRequests("openjdk", "jdk", "DUKE", "closed", "updated", null, 2, 3));
		assertEquals(1, result.path("items").size());
		assertEquals(body, result.path("items").get(0).path("body").asText());
		assertEquals(2, result.path("scanned").asInt());
		assertTrue(requests.get(0).getQuery().contains("direction=desc"));
		assertTrue(requests.get(0).getQuery().contains("state=closed"));
		assertTrue(requests.get(0).getQuery().contains("page=3"));
	}

	@Test
	void anEmptyFilteredPageStillAdvertisesTheNextPage() {
		routes.put("/repos/openjdk/jdk/pulls", new Reply(200, List.of(pr(1, "other", "Change")),
				Map.of("Link", "<https://api.github.com/repos/openjdk/jdk/pulls?page=2>; rel=\"next\"")));
		JsonNode result = tree(github.listPullRequests("openjdk", "jdk", "duke", null, null, null, 1, null));
		assertEquals(0, result.path("items").size());
		assertTrue(result.path("hasMore").asBoolean());
		assertEquals(2, result.path("nextPage").asInt());
	}

	@Test
	void invalidArgumentsFailBeforeAnyUpstreamRequests() {
		assertEquals(400, assertThrows(WebApplicationException.class,
				() -> github.getPullRequests("openjdk", "jdk", null, null, null, null, 0)).getResponse().getStatus());
		assertThrows(WebApplicationException.class, () -> github.listRepositories("openjdk", 101, 1));
		assertThrows(WebApplicationException.class, () -> github.listPullRequests("openjdk", "jdk", null, null, null, "desc&state=all", 10, 1));
		assertThrows(WebApplicationException.class, () -> github.getReviewContext("openjdk", "jdk", 0));
		assertThrows(WebApplicationException.class, () -> github.getBackportStatus("openjdk", "jdk", ORIGINAL, "openjdk/jdk21u@../bad", 10));
		assertTrue(requests.isEmpty());
	}

	@Test
	void rateLimitedRequestsRetainResetAndRetryHints() {
		routes.put("/users/limited/repos", new Reply(403, Map.of("message", "API rate limit exceeded"),
				Map.of("X-RateLimit-Remaining", "0", "X-RateLimit-Reset", "1234567890", "Retry-After", "60")));
		UpstreamException error = assertThrows(UpstreamException.class, () -> github.listRepositories("limited", 10, 1));
		assertEquals(429, error.status());
		assertEquals("1234567890", error.error().get("rateLimitReset"));
		assertEquals("60", error.error().get("retryAfter"));
	}

	@Test
	void reviewContextUsesCurrentHeadAndPreservesSkaraProgress() {
		reviewFixtures();
		JsonNode result = tree(github.getReviewContext("openjdk", "jdk", 1));
		assertEquals("complete", result.path("status").asText());
		assertEquals(HEAD, result.path("headSha").asText());
		assertTrue(result.path("reviews").get(0).path("atCurrentHead").asBoolean());
		assertFalse(result.path("reviews").get(1).path("atCurrentHead").asBoolean());
		assertTrue(result.path("skaraSections").path("Progress").asText().contains("CSR"));
		assertEquals("JDK-8341234", result.path("issues").get(0).path("id").asText());
		assertEquals("not_assessed", result.path("integrationReadiness").asText());
		assertEquals(4, result.path("sources").size());
	}

	@Test
	void unavailableChecksDoNotHideTheReviewOrClaimReadiness() {
		reviewFixtures();
		routes.put("/repos/openjdk/jdk/commits/" + HEAD + "/check-runs", new Reply(403, Map.of(), Map.of("X-RateLimit-Remaining", "0")));
		JsonNode result = tree(github.getReviewContext("openjdk", "jdk", 1));
		assertEquals("partial", result.path("status").asText());
		assertEquals(1, result.path("pullRequest").path("number").asInt());
		assertEquals("upstream_rate_limited", result.path("problems").get(0).path("error").asText());
		assertEquals("not_assessed", result.path("integrationReadiness").asText());
	}

	@Test
	void malformedOptionalReviewSectionsRemainPartial() {
		reviewFixtures();
		when("/repos/openjdk/jdk/pulls/1/reviews", Map.of("unexpected", "object"));
		when("/repos/openjdk/jdk/commits/" + HEAD + "/check-runs", Map.of("check_runs", "not an array"));
		when("/repos/openjdk/jdk/commits/" + HEAD + "/status", Map.of("statuses", List.of("not an object")));
		JsonNode result = tree(github.getReviewContext("openjdk", "jdk", 1));
		assertEquals("partial", result.path("status").asText());
		assertEquals(3, result.path("problems").size());
		assertEquals("unknown", result.path("commitStatus").path("availability").asText());
	}

	@Test
	void backportRequiresExactTrailerAndMembershipOfTheRequestedBranch() {
		backportFixtures();
		when("/search/commits", search(List.of(commit(BACKPORT, "8341234: Fix\n\nBackport-of: " + ORIGINAL))));
		when("/repos/openjdk/jdk21u/compare/" + BACKPORT + "..." + HEAD, Map.of("status", "ahead"));
		JsonNode target = firstTarget();
		assertEquals("found", target.path("status").asText());
		assertEquals("backport_commit", target.path("evidence").get(0).path("kind").asText());
		assertEquals(HEAD, target.path("targetHeadSha").asText());
	}

	@Test
	void aMatchingIssueNumberDoesNotProveABackport() {
		backportFixtures();
		when("/search/commits", search(List.of(commit(BACKPORT, "8341234: Fix"))));
		when("/search/issues", search(List.of(Map.of("number", 9, "title", "8341234: Fix", "body", "Backport fixes JDK-8341234"))));
		JsonNode target = firstTarget();
		assertEquals("not_found_in_search", target.path("status").asText());
		assertEquals(0, target.path("evidence").size());
	}

	@Test
	void aTrailerOnAnotherBranchDoesNotProveTheRequestedBackport() {
		backportFixtures();
		when("/search/commits", search(List.of(commit(BACKPORT, "Backport-of: " + ORIGINAL))));
		when("/repos/openjdk/jdk21u/compare/" + BACKPORT + "..." + HEAD, Map.of("status", "diverged"));
		assertEquals("not_found_in_search", firstTarget().path("status").asText());
	}

	@Test
	void anOpenBackportPullRequestIsEvidenceButIsNotReportedAsMerged() {
		backportFixtures();
		Map<String, Object> pull = pr(9, "duke", "Backport " + ORIGINAL);
		pull.put("merged", false);
		when("/search/issues", search(List.of(pull)));
		when("/repos/openjdk/jdk21u/pulls/9", pull);
		JsonNode target = firstTarget();
		assertEquals("found", target.path("status").asText());
		assertEquals("backport_pull_request", target.path("evidence").get(0).path("kind").asText());
		assertFalse(target.path("evidence").get(0).path("merged").asBoolean(true));
		assertEquals("open", target.path("evidence").get(0).path("state").asText());
	}

	@Test
	void searchFailuresArePartialAndMissingBranchesAreUnknown() {
		backportFixtures();
		routes.put("/search/commits", new Reply(403, Map.of(), Map.of("X-RateLimit-Remaining", "0")));
		assertEquals("partial", firstTarget().path("status").asText());
		JsonNode unknown = tree(github.getBackportStatus("openjdk", "jdk", ORIGINAL, "openjdk/jdk21u@missing", 10));
		assertEquals("unknown", unknown.path("targets").get(0).path("status").asText());
	}

	@Test
	void indexedSearchTruncationIsExplicit() {
		backportFixtures();
		when("/search/commits", Map.of("total_count", 20, "incomplete_results", true, "items", List.of()));
		JsonNode target = firstTarget();
		assertEquals("partial", target.path("status").asText());
		assertEquals("bounded_results", target.path("problems").get(0).path("error").asText());
	}

	@Test
	void malformedSearchAndComparisonCannotClaimAnEmptySearch() {
		backportFixtures();
		when("/search/commits", Map.of("items", "not an array"));
		when("/repos/openjdk/jdk21u/compare/" + ORIGINAL + "..." + HEAD, Map.of("unexpected", "object"));
		JsonNode target = firstTarget();
		assertEquals("partial", target.path("status").asText());
		assertEquals(2, target.path("problems").size());
	}

	@Test
	void originalCommitAlreadyInTargetHistoryNeedsNoSearch() {
		backportFixtures();
		when("/repos/openjdk/jdk21u/compare/" + ORIGINAL + "..." + HEAD, Map.of("status", "ahead"));
		JsonNode target = firstTarget();
		assertEquals("original_commit_in_target_history", target.path("evidence").get(0).path("kind").asText());
		assertFalse(requests.stream().anyMatch(uri -> uri.getPath().startsWith("/search/")));
	}

	@Test
	void skaraReviewersErrorsAndWarningsAreStructured() {
		reviewFixtures();
		JsonNode result = tree(github.getReviewContext("openjdk", "jdk", 1));
		JsonNode reviewers = result.path("skaraReviewers");
		assertEquals(3, reviewers.size());
		assertEquals("Alice Reviewer", reviewers.get(0).path("name").asText());
		assertEquals("alice", reviewers.get(0).path("openjdkId").asText());
		assertEquals("reviewer", reviewers.get(0).path("githubLogin").asText());
		assertEquals("Reviewer", reviewers.get(0).path("role").asText());
		assertFalse(reviewers.get(0).path("reReviewRequired").asBoolean());
		assertEquals("Committer", reviewers.get(1).path("role").asText());
		assertTrue(reviewers.get(1).path("reReviewRequired").asBoolean());
		assertEquals("no project role", reviewers.get(2).path("role").asText());
		assertFalse(reviewers.get(2).has("githubLogin"));
		assertEquals("Reviewer", result.path("reviews").get(0).path("skaraRole").asText());
		assertEquals("alice", result.path("reviews").get(0).path("openjdkId").asText());
		assertEquals("Committer", result.path("reviews").get(1).path("skaraRole").asText());
		assertEquals(1, result.path("skaraBlockers").size());
		assertTrue(result.path("skaraBlockers").get(0).asText().startsWith("⚠️ Pull request body is missing required line"));
		assertEquals("Careful", result.path("skaraWarnings").get(0).asText());
		assertTrue(result.path("skaraSections").has("Error"));
		assertTrue(result.path("assessment").asText().contains("integrated label"));
		assertFalse(result.path("pullRequest").path("integrated").asBoolean());
	}

	@Test
	void anIntegratedBackportPullRequestIsReportedByItsLabelNotByGitHubMerge() {
		backportFixtures();
		Map<String, Object> pull = pr(9, "duke", "Backport " + ORIGINAL);
		pull.put("state", "closed");
		pull.put("merged", false);
		pull.put("labels", List.of(Map.of("name", "integrated"), Map.of("name", "backport")));
		when("/search/issues", search(List.of(pull)));
		when("/repos/openjdk/jdk21u/pulls/9", pull);
		JsonNode evidence = firstTarget().path("evidence").get(0);
		assertTrue(evidence.path("integrated").asBoolean());
		assertFalse(evidence.path("merged").asBoolean());
		assertEquals("closed", evidence.path("state").asText());
		assertEquals("integrated", evidence.path("labels").get(0).asText());
	}

	@Test
	void triageGroupsOpenPullRequestsBySkaraStateAndAge() {
		Map<String, Object> ready = pr(1, "duke", "Ready");
		ready.put("labels", List.of(Map.of("name", "ready"), Map.of("name", "rfr")));
		ready.put("created_at", "2026-10-01T00:00:00Z");
		ready.put("updated_at", "2026-10-20T00:00:00Z");
		Map<String, Object> waiting = pr(2, "other", "Waiting");
		waiting.put("labels", List.of(Map.of("name", "rfr"), Map.of("name", "csr")));
		waiting.put("created_at", "2026-08-01T00:00:00Z");
		waiting.put("updated_at", "2026-09-01T00:00:00Z");
		Map<String, Object> blocked = pr(3, "newbie", "Blocked\n<!-- Skara -->\n### Progress\n- [ ] Review\n### Error\n&nbsp;⚠️ Pull request body is missing required line\n### Reviewing\nhow");
		blocked.put("labels", List.of());
		blocked.put("created_at", "2026-10-19T00:00:00Z");
		blocked.put("updated_at", "2026-10-21T00:00:00Z");
		Map<String, Object> draft = pr(4, "duke", "Draft");
		draft.put("draft", true);
		draft.put("labels", List.of());
		draft.put("created_at", "2026-01-01T00:00:00Z");
		draft.put("updated_at", "2026-01-01T00:00:00Z");
		routes.put("/repos/openjdk/jdk/pulls", new Reply(200, List.of(ready, waiting, blocked, draft),
				Map.of("Link", "<https://api.github.com/repos/openjdk/jdk/pulls?page=2>; rel=\"next\"")));
		JsonNode result = tree(github.getPullRequestTriage("openjdk", "jdk", 1, 30));
		assertEquals("complete", result.path("status").asText());
		assertEquals(4, result.path("scanned").asInt());
		assertTrue(result.path("hasMore").asBoolean());
		JsonNode groups = result.path("groups");
		assertEquals(1, groups.path("readyToIntegrate").path("count").asInt());
		assertEquals(1, groups.path("awaitingReview").path("count").asInt());
		assertEquals(2, groups.path("awaitingReview").path("items").get(0).path("number").asInt());
		assertEquals(1, groups.path("csrPending").path("count").asInt());
		assertEquals(1, groups.path("blockedByErrors").path("count").asInt());
		assertEquals(3, groups.path("blockedByErrors").path("items").get(0).path("number").asInt());
		assertEquals(1, groups.path("stale").path("count").asInt(), "Drafts are not stale; the waiting PR idle for 50 days is");
		assertEquals(50, groups.path("stale").path("items").get(0).path("idleDays").asInt());
		assertEquals(81, groups.path("stale").path("items").get(0).path("ageDays").asInt());
		assertEquals(1, groups.path("drafts").path("count").asInt());
		assertEquals(1, groups.path("unlabeled").path("count").asInt());
		assertEquals(2, result.path("labelCounts").path("rfr").asInt());
		assertTrue(requests.get(0).getQuery().contains("state=open"));
		assertEquals(400, assertThrows(WebApplicationException.class, () -> github.getPullRequestTriage("openjdk", "jdk", 4, null)).getResponse().getStatus());
		assertEquals(400, assertThrows(WebApplicationException.class, () -> github.getPullRequestTriage("openjdk", "jdk", null, 0)).getResponse().getStatus());
	}

	@Test
	void contributorRecordCountsIntegratedPullRequestsBySkaraLabel() {
		Map<String, Object> first = pr(5, "duke", "First");
		first.put("closed_at", "2024-01-05T00:00:00Z");
		first.put("labels", List.of(Map.of("name", "integrated")));
		Map<String, Object> last = pr(90, "duke", "Latest");
		last.put("closed_at", "2026-10-01T00:00:00Z");
		last.put("labels", List.of(Map.of("name", "integrated")));
		searches.put("order=asc", new Reply(200, Map.of("total_count", 12, "incomplete_results", false, "items", List.of(first)), Map.of()));
		searches.put("order=desc", new Reply(200, Map.of("total_count", 12, "incomplete_results", false, "items", List.of(last)), Map.of()));
		searches.put("is:open", new Reply(200, search(List.of()), Map.of()));
		searches.put("-label:integrated", new Reply(200, Map.of("total_count", 2, "incomplete_results", false, "items", List.of()), Map.of()));
		JsonNode result = tree(github.getContributorRecord("openjdk", "jdk", "duke", 5));
		assertEquals("complete", result.path("status").asText());
		assertEquals(12, result.path("integratedPullRequests").path("total").asInt());
		assertEquals("2024-01-05T00:00:00Z", result.path("integratedPullRequests").path("firstIntegratedAt").asText());
		assertEquals("2026-10-01T00:00:00Z", result.path("integratedPullRequests").path("lastIntegratedAt").asText());
		assertEquals(90, result.path("integratedPullRequests").path("latest").get(0).path("number").asInt());
		assertEquals(0, result.path("openPullRequests").asInt());
		assertEquals(2, result.path("closedWithoutIntegration").asInt());
		assertEquals(4, requests.size());
		assertTrue(requests.stream().allMatch(uri -> uri.getQuery().contains("author:duke")));
		assertTrue(requests.stream().noneMatch(uri -> uri.getQuery().contains("is:merged")), "Skara never merges on GitHub");
		assertThrows(WebApplicationException.class, () -> github.getContributorRecord("openjdk", "jdk", "duke", 51));
		assertThrows(WebApplicationException.class, () -> github.getContributorRecord("openjdk", "jdk", "du ke", null));
	}

	@Test
	void projectActivityRanksAuthorsAndDetectsFirstTimeContributors() {
		Map<String, Object> a = pr(1, "duke", "One");
		a.put("labels", List.of(Map.of("name", "integrated")));
		a.put("closed_at", "2026-07-02T00:00:00Z");
		Map<String, Object> b = pr(2, "duke", "Two");
		b.put("labels", List.of(Map.of("name", "integrated")));
		Map<String, Object> c = pr(3, "newbie", "Three");
		c.put("labels", List.of(Map.of("name", "integrated")));
		searches.put("label:integrated closed:2026-07-01..2026-09-30", new Reply(200, Map.of("total_count", 3, "incomplete_results", false, "items", List.of(a, b, c)), Map.of()));
		searches.put("created:2026-07-01..2026-09-30", new Reply(200, Map.of("total_count", 5, "incomplete_results", false, "items", List.of()), Map.of()));
		searches.put("author:duke label:integrated closed:<2026-07-01", new Reply(200, Map.of("total_count", 4, "incomplete_results", false, "items", List.of()), Map.of()));
		searches.put("author:newbie label:integrated closed:<2026-07-01", new Reply(200, Map.of("total_count", 0, "incomplete_results", false, "items", List.of()), Map.of()));
		JsonNode result = tree(github.getProjectActivity("openjdk", "jdk", "2026-07-01", "2026-09-30", null));
		assertEquals("complete", result.path("status").asText());
		assertEquals(3, result.path("integrated").path("total").asInt());
		assertEquals("duke", result.path("integrated").path("authors").get(0).path("login").asText());
		assertEquals(2, result.path("integrated").path("authors").get(0).path("integrated").asInt());
		assertEquals(5, result.path("openedPullRequests").asInt());
		assertEquals(1, result.path("firstTimeContributors").size());
		assertEquals("newbie", result.path("firstTimeContributors").get(0).asText());
		assertEquals(2, result.path("firstTimeCheck").path("checkedAuthors").asInt());
		assertEquals(3, result.path("integrated").path("recent").size());
		JsonNode partial = tree(github.getProjectActivity("openjdk", "jdk", "2026-07-01", null, null));
		assertEquals("partial", partial.path("status").asText(), "Unmatched search windows are reported, not hidden");
		assertEquals(400, assertThrows(WebApplicationException.class, () -> github.getProjectActivity("openjdk", "jdk", "2026-09-30", "2026-07-01", null)).getResponse().getStatus());
		assertEquals(400, assertThrows(WebApplicationException.class, () -> github.getProjectActivity("openjdk", "jdk", "2024-01-01", "2026-07-01", null)).getResponse().getStatus());
		assertEquals(400, assertThrows(WebApplicationException.class, () -> github.getProjectActivity("openjdk", "jdk", "July", null, null)).getResponse().getStatus());
	}

	private void reviewFixtures() {
		when("/repos/openjdk/jdk/pulls/1", pr(1, "duke", "Description\n<!-- Skara -->\n### Progress\n- [ ] CSR approval\n### Issue\nJDK-8341234\n"
				+ "### Error\n&nbsp;⚠️ Pull request body is missing required line: `- [x] I confirm`\n### Warning\n * Careful\n"
				+ "### Reviewers\n * [Alice Reviewer](https://openjdk.org/census#alice) (@reviewer - **Reviewer**)\n"
				+ " * [Bob Committer](https://openjdk.org/census#bob) (@other - **Committer**) 🔄 Re-review required (review applies to " + OLD + ")\n"
				+ " * [Carol](https://openjdk.org/census#carol) (no project role)\n### Reviewing\nHow to review"));
		when("/repos/openjdk/jdk/pulls/1/reviews", List.of(
				Map.of("state", "APPROVED", "commit_id", HEAD, "user", Map.of("login", "reviewer")),
				Map.of("state", "CHANGES_REQUESTED", "commit_id", OLD, "user", Map.of("login", "other"))));
		when("/repos/openjdk/jdk/commits/" + HEAD + "/check-runs", Map.of("total_count", 1, "check_runs",
				List.of(Map.of("name", "jcheck", "status", "completed", "conclusion", "success", "head_sha", HEAD))));
		when("/repos/openjdk/jdk/commits/" + HEAD + "/status", Map.of("sha", HEAD, "state", "success", "total_count", 0, "statuses", List.of()));
	}

	private void backportFixtures() {
		when("/repos/openjdk/jdk/commits/" + ORIGINAL, commit(ORIGINAL, "8341234: Fix"));
		when("/repos/openjdk/jdk21u", Map.of("default_branch", "master"));
		when("/repos/openjdk/jdk21u/branches/master", Map.of("commit", Map.of("sha", HEAD)));
		when("/search/commits", search(List.of()));
		when("/search/issues", search(List.of()));
	}

	private JsonNode firstTarget() {
		return tree(github.getBackportStatus("openjdk", "jdk", ORIGINAL, "openjdk/jdk21u", 10)).path("targets").get(0);
	}

	private void when(String path, Object body) { routes.put(path, new Reply(200, body, Map.of())); }
	private JsonNode tree(Object value) { return json.valueToTree(value); }
	private static Map<String, Object> repo(String name) { return Map.of("name", name, "stargazers_count", 5); }
	private static Map<String, Object> search(List<?> items) { return Map.of("total_count", items.size(), "incomplete_results", false, "items", items); }
	private static Map<String, Object> commit(String sha, String message) {
		return Map.of("sha", sha, "html_url", "https://github.com/openjdk/jdk/commit/" + sha, "commit", Map.of("message", message));
	}
	private static Map<String, Object> pr(int number, String user, String body) {
		return new java.util.LinkedHashMap<>(Map.of("number", number, "title", "8341234: Fix", "body", body,
				"state", "open", "head", Map.of("sha", HEAD, "ref", "fix"), "base", Map.of("sha", OLD, "ref", "master"),
				"user", Map.of("login", user), "html_url", "https://github.com/openjdk/jdk/pull/" + number));
	}
	private record Reply(int status, Object body, Map<String, String> headers) { }
}
