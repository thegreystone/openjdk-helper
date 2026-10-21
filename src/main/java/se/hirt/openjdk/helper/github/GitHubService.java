/*
 * Copyright (C) 2024-2026 Marcus Hirt
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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import se.hirt.openjdk.helper.common.ApiSupport;
import se.hirt.openjdk.helper.common.UpstreamException;
import se.hirt.openjdk.helper.common.UpstreamHttp;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Bounded GitHub queries and OpenJDK review evidence, shared by REST and MCP. */
@ApplicationScoped
public class GitHubService {
	private static final Pattern ISSUE = Pattern.compile("\\b(?:JDK|JMC|CODETOOLS|SKARA)-[0-9]{3,9}\\b",
			Pattern.CASE_INSENSITIVE);
	private final UpstreamHttp upstream;
	private final ObjectMapper json;
	private final String base;
	private final String token;
	private java.time.Clock clock = java.time.Clock.systemUTC();

	@Inject
	public GitHubService(UpstreamHttp upstream, ObjectMapper json,
			@ConfigProperty(name = "openjdk.github.api-base-url", defaultValue = "https://api.github.com")
			String base, @ConfigProperty(name = "openjdk.github.token")
			Optional<String> token) {
		this.upstream = upstream;
		this.json = json;
		this.base = base;
		this.token = token.orElse(null);
	}

	java.time.Clock clock() {
		return clock;
	}

	/** Tests pin the clock so age calculations are deterministic. */
	void clock(java.time.Clock value) {
		this.clock = value;
	}

	public Response getRepositories(String user) {
		return getRepositories(user, null, null);
	}

	public Response getRepositories(String user, Integer limit, Integer page) {
		return arrayResponse(listRepositories(user, limit, page));
	}

	public Map<String, Object> listRepositories(String user, Integer limit, Integer page) {
		user = ApiSupport.requireName(user, "user");
		int size = ApiSupport.limit(limit);
		int currentPage = page(page);
		Fetch result = fetch("users/" + ApiSupport.segment(user) + "/repos",
				Map.of("per_page", size, "page", currentPage, "sort", "full_name", "direction", "asc"));
		List<Map<String, Object>> repos = new ArrayList<>();
		for (JsonNode repo : array(result.data(), result)) {
			Map<String, Object> item = fields(repo, "name", "description", "full_name", "html_url", "default_branch",
					"fork", "archived", "created_at", "pushed_at", "updated_at");
			item.put("stars", repo.path("stargazers_count").asInt());
			repos.add(item);
		}
		return pageResult(repos, currentPage, size, result);
	}

	/** The original REST signature remains available; -1 now means a bounded 100-item page. */
	public Response getPullRequests(
		String owner, String repo, String user, String state, String sort, String direction, int maxResults) {
		return getPullRequests(owner, repo, user, state, sort, direction, maxResults, null);
	}

	public Response getPullRequests(
		String owner, String repo, String user, String state, String sort, String direction, int maxResults,
		Integer page) {
		return arrayResponse(
				listPullRequests(owner, repo, user, state, sort, direction, maxResults == -1 ? 100 : maxResults, page));
	}

	public Map<String, Object> listPullRequests(
		String owner, String repo, String user, String state, String sort, String direction, Integer limit,
		Integer page) {
		String repository = repository(owner, repo);
		String author = user == null || user.isBlank() ? null : ApiSupport.requireName(user.trim(), "user");
		int size = ApiSupport.limit(limit);
		int currentPage = page(page);
		Fetch result = fetch("repos/" + repository + "/pulls",
				Map.of("state", choice(state, "open", "state", "open", "closed", "all"), "sort",
						choice(sort, "created", "sort", "created", "updated", "popularity", "long-running"),
						"direction", choice(direction, "desc", "direction", "asc", "desc"), "per_page", size, "page",
						currentPage));
		List<Map<String, Object>> pulls = new ArrayList<>();
		JsonNode upstreamItems = array(result.data(), result);
		for (JsonNode pull : upstreamItems) {
			if (author == null || author.equalsIgnoreCase(pull.path("user").path("login").asText()))
				pulls.add(pullSummary(pull));
		}
		Map<String, Object> response = pageResult(pulls, currentPage, size, result);
		response.put("scanned", upstreamItems.size());
		if (author != null) {
			response.put("author", author);
			response.put("filterScope",
					"Author filter applies to this upstream page; follow nextPage even when items is empty");
		}
		return response;
	}

	public Map<String, Object> getReviewContext(String owner, String repo, int number) {
		String repository = repository(owner, repo);
		if (number < 1)
			throw ApiSupport.badRequest("number must be positive");
		Fetch pr = fetch("repos/" + repository + "/pulls/" + number, Map.of());
		String sha = pr.data().path("head").path("sha").asText();
		if (!sha.matches("[0-9a-fA-F]{40}"))
			throw malformed(pr, "Pull request has no valid head commit");
		List<Map<String, Object>> sources = new ArrayList<>();
		sources.add(pr.source());
		List<Map<String, Object>> problems = new ArrayList<>();
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("repository", owner + "/" + repo);
		response.put("pullRequest", pullSummary(pr.data()));
		response.put("headSha", sha);
		response.put("issues", issues(pr.data().path("title").asText() + "\n" + pr.data().path("body").asText()));
		Map<String, String> sections = skaraSections(pr.data().path("body").asText());
		response.put("skaraSections", sections);
		List<Map<String, Object>> skaraReviewers = skaraReviewers(sections);
		response.put("skaraReviewers", skaraReviewers);
		response.put("skaraBlockers", skaraItems(sections, "Error"));
		response.put("skaraWarnings", skaraItems(sections, "Warning"));
		Fetch reviewData = optional("repos/" + repository + "/pulls/" + number + "/reviews", Map.of("per_page", 100),
				sources, problems);
		List<Map<String, Object>> reviews = new ArrayList<>();
		if (hasArray(reviewData, null, problems)) {
			for (JsonNode review : reviewData.data()) {
				Map<String, Object> item = fields(review, "id", "state", "body", "html_url", "submitted_at",
						"commit_id");
				String login = review.path("user").path("login").asText();
				item.put("user", login);
				item.put("atCurrentHead", sha.equalsIgnoreCase(review.path("commit_id").asText()));
				skaraReviewers.stream()
						.filter(reviewer -> login.equalsIgnoreCase(String.valueOf(reviewer.get("githubLogin"))))
						.findFirst().ifPresent(reviewer -> {
							item.put("openjdkId", reviewer.get("openjdkId"));
							item.put("skaraRole", reviewer.get("role"));
						});
				reviews.add(item);
			}
			markPagination(reviewData, reviewData.data().size(), problems);
		}
		response.put("reviews", reviews);
		response.put("reviewRoleSemantics",
				"skaraRole and openjdkId come from the Skara Reviewers section, which reflects census roles when Skara rendered it; reviewers absent from that section have no project role recorded by Skara.");
		Fetch checkData = optional("repos/" + repository + "/commits/" + sha + "/check-runs", Map.of("per_page", 100),
				sources, problems);
		List<Map<String, Object>> checks = new ArrayList<>();
		if (hasArray(checkData, "check_runs", problems)) {
			for (JsonNode check : checkData.data().path("check_runs"))
				checks.add(fields(check, "name", "status", "conclusion", "head_sha", "details_url", "html_url",
						"started_at", "completed_at", "output"));
			markPagination(checkData, checks.size(), problems);
		}
		response.put("checks", checks);
		Fetch statusData = optional("repos/" + repository + "/commits/" + sha + "/status", Map.of("per_page", 100),
				sources, problems);
		boolean statusAvailable = hasArray(statusData, "statuses", problems);
		response.put("commitStatus", !statusAvailable ? Map.of("availability", "unknown")
				: fields(statusData.data(), "state", "sha", "statuses", "total_count"));
		if (statusAvailable)
			markPagination(statusData, statusData.data().path("statuses").size(), problems);
		response.put("status", problems.isEmpty() ? "complete" : "partial");
		response.put("integrationReadiness", "not_assessed");
		response.put("assessment",
				"These are source observations at the recorded head commit. Skara, project review rules, CSR/JEP and update approvals determine integration readiness. "
						+ "Skara integrates by pushing the commit and closing the pull request, so GitHub's merged flag stays false; the integrated label marks integration.");
		response.put("sources", sources);
		response.put("problems", problems);
		return response;
	}

	public Map<String, Object> getBackportStatus(
		String owner, String repo, String commit, String targets, Integer limit) {
		return new BackportLookup(this).lookup(owner, repo, commit, targets, limit);
	}

	public Map<String, Object> getPullRequestTriage(String owner, String repo, Integer pages, Integer staleDays) {
		return new GitHubInsights(this).triage(owner, repo, pages, staleDays);
	}

	public Map<String, Object> getContributorRecord(String owner, String repo, String login, Integer limit) {
		return new GitHubInsights(this).contributorRecord(owner, repo, login, limit);
	}

	public Map<String, Object> getProjectActivity(
		String owner, String repo, String since, String until, Integer pages) {
		return new GitHubInsights(this).activity(owner, repo, since, until, pages);
	}

	Fetch optional(
		String path, Map<String, ?> query, List<Map<String, Object>> sources, List<Map<String, Object>> problems) {
		try {
			Fetch result = fetch(path, query);
			sources.add(result.source());
			return result;
		} catch (UpstreamException e) {
			problems.add(e.error());
			return null;
		}
	}

	Fetch fetch(String path, Map<String, ?> query) {
		URI uri = ApiSupport.uri(base, path, query);
		var response = upstream.get(uri, token);
		try {
			JsonNode body = json.readTree(response.body());
			if (body == null)
				throw new java.io.IOException("Empty JSON response");
			Map<String, Object> source = Map.of("url", uri.toString(), "fetchedAt", response.fetchedAt().toString(),
					"cached", response.cached());
			boolean next = response.headers().firstValue("Link").map(value -> value.contains("rel=\"next\""))
					.orElse(false);
			return new Fetch(body, source, next);
		} catch (java.io.IOException e) {
			throw new UpstreamException(uri, 502, "GitHub returned invalid JSON", Map.of());
		}
	}

	static void markPagination(Fetch result, int returned, List<Map<String, Object>> problems) {
		if (result.hasMore() || result.data().path("incomplete_results").asBoolean()
				|| result.data().path("total_count").asInt(returned) > returned)
			problems.add(Map.of("error", "bounded_results", "message", "Additional or unindexed results may exist",
					"source", result.source()));
	}

	static boolean hasArray(Fetch result, String field, List<Map<String, Object>> problems) {
		if (result == null)
			return false;
		JsonNode node = field == null ? result.data() : result.data().path(field);
		if (node.isArray()) {
			boolean allObjects = true;
			for (JsonNode item : node)
				if (!item.isObject())
					allObjects = false;
			if (allObjects)
				return true;
		}
		problems.add(malformed(result, "GitHub did not return an object array" + (field == null ? "" : " in " + field))
				.error());
		return false;
	}

	Map<String, Object> pullSummary(JsonNode pull) {
		Map<String, Object> result = fields(pull, "number", "state", "title", "body", "created_at", "updated_at",
				"closed_at", "merged_at", "html_url", "draft", "labels", "merged", "mergeable");
		result.put("user", pull.path("user").path("login").asText());
		// Skara pushes commits itself and closes the PR; GitHub's merged flag never reflects OpenJDK integration.
		result.put("integrated", hasLabel(pull, "integrated"));
		// Avoid repeating whole repository/user documents nested in GitHub's base and head objects.
		result.put("base", fields(pull.path("base"), "label", "ref", "sha"));
		result.put("head", fields(pull.path("head"), "label", "ref", "sha"));
		List<String> requested = new ArrayList<>();
		pull.path("requested_reviewers").forEach(reviewer -> requested.add(reviewer.path("login").asText()));
		result.put("requested_reviewers", requested);
		return result;
	}

	Map<String, Object> fields(JsonNode object, String ... fields) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (String field : fields)
			if (object.has(field))
				result.put(field, json.convertValue(object.get(field), Object.class));
		return result;
	}

	private static Map<String, Object> pageResult(List<Map<String, Object>> items, int page, int limit, Fetch result) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("items", items);
		response.put("page", page);
		response.put("limit", limit);
		response.put("hasMore", result.hasMore());
		response.put("nextPage", result.hasMore() ? page + 1 : null);
		response.put("source", result.source());
		return response;
	}

	private static Response arrayResponse(Map<String, Object> result) {
		Response.ResponseBuilder response = Response.ok(result.get("items")).header("X-Page", result.get("page"))
				.header("X-Page-Size", result.get("limit")).header("X-Has-More", result.get("hasMore"));
		if (result.get("nextPage") != null)
			response.header("X-Next-Page", result.get("nextPage"));
		return response.build();
	}

	private static int page(Integer page) {
		if (page != null && (page < 1 || page > 10000))
			throw ApiSupport.badRequest("page must be between 1 and 10000");
		return page == null ? 1 : page;
	}

	private static String choice(String value, String defaultValue, String label, String ... choices) {
		String actual = value == null || value.isBlank() ? defaultValue : value.trim();
		if (!Set.of(choices).contains(actual))
			throw ApiSupport.badRequest(label + " must be one of " + String.join(", ", choices));
		return actual;
	}

	static String repository(String owner, String repo) {
		return ApiSupport.segment(ApiSupport.requireName(owner, "owner")) + "/"
				+ ApiSupport.segment(ApiSupport.requireName(repo, "repo"));
	}

	static List<Map<String, String>> issues(String text) {
		Set<String> ids = new LinkedHashSet<>();
		var matcher = ISSUE.matcher(text);
		while (matcher.find())
			ids.add(matcher.group().toUpperCase(java.util.Locale.ROOT));
		var title = Pattern.compile("^([124-8][0-9]{6}):").matcher(text);
		if (title.find())
			ids.add("JDK-" + title.group(1));
		return ids.stream().map(id -> Map.of("id", id, "url", "https://bugs.openjdk.org/browse/" + id)).toList();
	}

	static Map<String, String> skaraSections(String body) {
		Map<String, String> sections = new LinkedHashMap<>();
		var headings = Pattern.compile("(?m)^#{2,3}\\s+([^\\r\\n]+)\\r?$").matcher(body);
		String heading = null;
		int start = 0;
		while (headings.find()) {
			if (heading != null)
				sections.put(heading, body.substring(start, headings.start()).trim());
			String name = headings.group(1).trim();
			heading = name.matches(
					"(?i)Progress|Issues?|Reviewers?|Contributors?|Reviewing|Webrev|Errors?|Warnings?|Integration blocker.*")
							? name : null;
			start = headings.end();
		}
		if (heading != null)
			sections.put(heading, body.substring(start).trim());
		return sections;
	}

	private static final Pattern SKARA_REVIEWER = Pattern.compile(
			"\\[([^\\]]+)\\]\\(https://openjdk\\.org/census#([A-Za-z0-9_.-]+)\\)\\s*\\((?:@([A-Za-z0-9-]+)\\s*-\\s*)?(?:\\*\\*([^*]+)\\*\\*|([^)]+))\\)([^\\r\\n]*)");

	/**
	 * Skara renders "[Name](https://openjdk.org/census#id) (@login - **Role**)" for each reviewer.
	 */
	static List<Map<String, Object>> skaraReviewers(Map<String, String> sections) {
		List<Map<String, Object>> reviewers = new ArrayList<>();
		for (var section : sections.entrySet()) {
			if (!section.getKey().matches("(?i)Reviewers?"))
				continue;
			var matcher = SKARA_REVIEWER.matcher(section.getValue());
			while (matcher.find()) {
				Map<String, Object> reviewer = new LinkedHashMap<>();
				reviewer.put("name", matcher.group(1).trim());
				reviewer.put("openjdkId", matcher.group(2));
				if (matcher.group(3) != null)
					reviewer.put("githubLogin", matcher.group(3));
				reviewer.put("role", (matcher.group(4) != null ? matcher.group(4) : matcher.group(5)).trim());
				reviewer.put("reReviewRequired",
						matcher.group(6).toLowerCase(java.util.Locale.ROOT).contains("re-review required"));
				reviewers.add(reviewer);
			}
		}
		return reviewers;
	}

	/** Bulleted lines of a Skara Error or Warning section, without markdown list markers. */
	static List<String> skaraItems(Map<String, String> sections, String name) {
		List<String> items = new ArrayList<>();
		for (var section : sections.entrySet()) {
			if (!section.getKey().matches("(?i)" + name + "s?"))
				continue;
			for (String line : section.getValue().split("\\R")) {
				String text = line.replaceFirst("^\\s*(?:[-*]|&nbsp;)?\\s*", "").replace("&nbsp;", " ").strip();
				if (!text.isEmpty())
					items.add(text.length() > 1000 ? text.substring(0, 1000) : text);
			}
		}
		return items;
	}

	static boolean hasLabel(JsonNode pull, String name) {
		for (JsonNode label : pull.path("labels"))
			if (name.equalsIgnoreCase(label.path("name").asText()))
				return true;
		return false;
	}

	static List<String> labelNames(JsonNode pull) {
		List<String> names = new ArrayList<>();
		for (JsonNode label : pull.path("labels"))
			if (label.path("name").isTextual())
				names.add(label.path("name").asText());
		return names;
	}

	private static JsonNode array(JsonNode node, Fetch source) {
		if (!node.isArray())
			throw malformed(source, "GitHub did not return an array");
		return node;
	}

	static UpstreamException malformed(Fetch result, String message) {
		return new UpstreamException(URI.create(result.source().get("url").toString()), 502, message, Map.of());
	}

	record Fetch(JsonNode data, Map<String, Object> source, boolean hasMore) {
	}
}
