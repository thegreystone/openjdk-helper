/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.github;

import com.fasterxml.jackson.databind.JsonNode;
import se.hirt.openjdk.helper.common.ApiSupport;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Lead-oriented summaries built from bounded GitHub list and search requests; Skara labels, never GitHub merge state, signal integration. */
final class GitHubInsights {
	private static final int ITEMS_PER_GROUP = 25;
	private static final int MAX_PAGES = 3;
	private static final int FIRST_TIME_CHECKS = 10;
	private final GitHubService github;

	GitHubInsights(GitHubService github) {
		this.github = github;
	}

	Map<String, Object> triage(String owner, String repo, Integer pages, Integer staleDays) {
		String repository = GitHubService.repository(owner, repo);
		int pageCount = pages(pages);
		int stale = staleDays == null ? 30 : staleDays;
		if (stale < 1 || stale > 365) throw ApiSupport.badRequest("staleDays must be between 1 and 365");
		List<Map<String, Object>> sources = new ArrayList<>();
		List<Map<String, Object>> problems = new ArrayList<>();
		Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
		for (String name : List.of("readyToIntegrate", "awaitingReview", "blockedByErrors", "csrPending", "mergeConflict", "stale", "drafts", "unlabeled")) {
			groups.put(name, new ArrayList<>());
		}
		Map<String, Integer> labelCounts = new TreeMap<>();
		Instant now = github.clock().instant();
		int scanned = 0;
		boolean hasMore = false;
		for (int page = 1; page <= pageCount; page++) {
			var result = github.optional("repos/" + repository + "/pulls", Map.of("state", "open", "sort", "updated", "direction", "desc",
					"per_page", 100, "page", page), sources, problems);
			if (!GitHubService.hasArray(result, null, problems)) break;
			for (JsonNode pull : result.data()) {
				scanned++;
				Map<String, Object> item = github.fields(pull, "number", "title", "draft", "created_at", "updated_at", "html_url");
				item.put("user", pull.path("user").path("login").asText());
				List<String> labels = GitHubService.labelNames(pull);
				item.put("labels", labels);
				labels.forEach(label -> labelCounts.merge(label, 1, Integer::sum));
				long ageDays = days(pull.path("created_at").asText(), now);
				long idleDays = days(pull.path("updated_at").asText(), now);
				item.put("ageDays", ageDays);
				item.put("idleDays", idleDays);
				Map<String, String> sections = GitHubService.skaraSections(pull.path("body").asText());
				List<String> blockers = GitHubService.skaraItems(sections, "Error");
				item.put("skaraBlockers", blockers);
				item.put("skaraWarnings", GitHubService.skaraItems(sections, "Warning"));
				boolean draft = pull.path("draft").asBoolean();
				boolean ready = GitHubService.hasLabel(pull, "ready");
				boolean rfr = GitHubService.hasLabel(pull, "rfr");
				if (ready) groups.get("readyToIntegrate").add(item);
				if (rfr && !ready) groups.get("awaitingReview").add(item);
				if (!blockers.isEmpty()) groups.get("blockedByErrors").add(item);
				if (GitHubService.hasLabel(pull, "csr")) groups.get("csrPending").add(item);
				if (GitHubService.hasLabel(pull, "merge-conflict")) groups.get("mergeConflict").add(item);
				if (!draft && idleDays >= stale) groups.get("stale").add(item);
				if (draft) groups.get("drafts").add(item);
				if (labels.isEmpty() && !draft) groups.get("unlabeled").add(item);
			}
			hasMore = result.hasMore();
			if (!hasMore) break;
		}
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("repository", owner + "/" + repo);
		response.put("scanned", scanned);
		response.put("pages", pageCount);
		response.put("hasMore", hasMore);
		response.put("staleDays", stale);
		Map<String, Object> summary = new LinkedHashMap<>();
		for (var group : groups.entrySet()) {
			List<Map<String, Object>> items = group.getValue();
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("count", items.size());
			entry.put("items", items.subList(0, Math.min(items.size(), ITEMS_PER_GROUP)));
			entry.put("truncated", items.size() > ITEMS_PER_GROUP);
			summary.put(group.getKey(), entry);
		}
		response.put("groups", summary);
		response.put("labelCounts", labelCounts);
		response.put("groupSemantics", "Groups come from Skara labels (ready, rfr, csr, merge-conflict), draft state, Skara Error sections and idle time since the last GitHub update. A pull request can appear in several groups. Open pull requests beyond the scanned pages are not included.");
		response.put("status", problems.isEmpty() ? "complete" : "partial");
		response.put("sources", sources);
		response.put("problems", problems);
		return response;
	}

	Map<String, Object> contributorRecord(String owner, String repo, String login, Integer limit) {
		String repository = GitHubService.repository(owner, repo);
		String author = ApiSupport.requireName(login, "login");
		int size = limit == null ? 10 : limit;
		if (size < 1 || size > 50) throw ApiSupport.badRequest("limit must be between 1 and 50");
		List<Map<String, Object>> sources = new ArrayList<>();
		List<Map<String, Object>> problems = new ArrayList<>();
		String base = "repo:" + repository + " is:pr author:" + author;
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("repository", owner + "/" + repo);
		response.put("login", author);
		Map<String, Object> integrated = new LinkedHashMap<>();
		var earliest = github.optional("search/issues", Map.of("q", base + " label:integrated", "sort", "created", "order", "asc", "per_page", size), sources, problems);
		var latest = github.optional("search/issues", Map.of("q", base + " label:integrated", "sort", "created", "order", "desc", "per_page", size), sources, problems);
		List<Map<String, Object>> first = items(earliest);
		List<Map<String, Object>> last = items(latest);
		integrated.put("total", total(earliest, problems));
		integrated.put("firstIntegratedAt", first.isEmpty() ? null : first.get(0).get("closed_at"));
		integrated.put("lastIntegratedAt", last.isEmpty() ? null : last.get(0).get("closed_at"));
		integrated.put("earliest", first);
		integrated.put("latest", last);
		response.put("integratedPullRequests", integrated);
		var open = github.optional("search/issues", Map.of("q", base + " is:open", "per_page", 1), sources, problems);
		response.put("openPullRequests", total(open, problems));
		var abandoned = github.optional("search/issues", Map.of("q", base + " is:closed -label:integrated", "per_page", 1), sources, problems);
		response.put("closedWithoutIntegration", total(abandoned, problems));
		response.put("semantics", "Counts use GitHub search for pull requests by this GitHub author carrying the Skara integrated label; sponsored changes count for the pull request author. Totals can lag GitHub indexing. Significance of contributions is not assessed; Committer and Reviewer criteria are set by each project.");
		response.put("status", problems.isEmpty() ? "complete" : "partial");
		response.put("sources", sources);
		response.put("problems", problems);
		return response;
	}

	Map<String, Object> activity(String owner, String repo, String since, String until, Integer pages) {
		String repository = GitHubService.repository(owner, repo);
		LocalDate today = LocalDate.ofInstant(github.clock().instant(), ZoneOffset.UTC);
		LocalDate start = date(since, "since");
		LocalDate end = until == null || until.isBlank() ? today : date(until, "until");
		if (end.isBefore(start)) throw ApiSupport.badRequest("until must not be before since");
		if (Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() > 366) throw ApiSupport.badRequest("The period must not exceed 366 days");
		int pageCount = pages(pages);
		String window = start + ".." + end;
		List<Map<String, Object>> sources = new ArrayList<>();
		List<Map<String, Object>> problems = new ArrayList<>();
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("repository", owner + "/" + repo);
		response.put("since", start.toString());
		response.put("until", end.toString());
		Map<String, Integer> authors = new LinkedHashMap<>();
		List<Map<String, Object>> recent = new ArrayList<>();
		int scanned = 0;
		boolean hasMore = false;
		Integer total = null;
		for (int page = 1; page <= pageCount; page++) {
			var result = github.optional("search/issues", Map.of("q", "repo:" + repository + " is:pr label:integrated closed:" + window,
					"sort", "updated", "order", "desc", "per_page", 100, "page", page), sources, problems);
			if (!GitHubService.hasArray(result, "items", problems)) break;
			if (total == null) total = total(result, problems);
			for (JsonNode item : result.data().path("items")) {
				scanned++;
				authors.merge(item.path("user").path("login").asText(), 1, Integer::sum);
				if (recent.size() < ITEMS_PER_GROUP) recent.add(summary(item));
			}
			hasMore = result.hasMore();
			if (!hasMore) break;
		}
		Map<String, Object> integrated = new LinkedHashMap<>();
		integrated.put("total", total);
		integrated.put("scanned", scanned);
		integrated.put("hasMore", hasMore);
		List<Map<String, Object>> ranked = authors.entrySet().stream()
				.sorted((a, b) -> b.getValue().equals(a.getValue()) ? a.getKey().compareTo(b.getKey()) : b.getValue() - a.getValue())
				.map(entry -> Map.<String, Object>of("login", entry.getKey(), "integrated", entry.getValue())).toList();
		integrated.put("authors", ranked);
		integrated.put("recent", recent);
		response.put("integrated", integrated);
		var opened = github.optional("search/issues", Map.of("q", "repo:" + repository + " is:pr created:" + window, "per_page", 1), sources, problems);
		response.put("openedPullRequests", total(opened, problems));
		List<String> firstTime = new ArrayList<>();
		int checked = 0;
		for (Map<String, Object> entry : ranked) {
			if (checked >= FIRST_TIME_CHECKS) break;
			String login = entry.get("login").toString();
			if (!login.matches("[A-Za-z0-9_.-]{1,100}")) continue;
			var earlier = github.optional("search/issues", Map.of("q", "repo:" + repository + " is:pr author:" + login + " label:integrated closed:<" + start, "per_page", 1), sources, problems);
			checked++;
			Integer before = total(earlier, problems);
			if (before != null && before == 0) firstTime.add(login);
		}
		response.put("firstTimeContributors", firstTime);
		response.put("firstTimeCheck", Map.of("checkedAuthors", checked, "uncheckedAuthors", Math.max(0, ranked.size() - checked)));
		response.put("semantics", "Integrated means the Skara integrated label with the pull request closed inside the period; GitHub never records these as merged. First-time contributors are scanned authors with no earlier integrated pull request in this repository, checked for the most active authors only. Use searchIssues with resolvedSince for the matching JBS view.");
		response.put("status", problems.isEmpty() ? "complete" : "partial");
		response.put("sources", sources);
		response.put("problems", problems);
		return response;
	}

	private Map<String, Object> summary(JsonNode item) {
		Map<String, Object> result = github.fields(item, "number", "title", "created_at", "closed_at", "html_url");
		result.put("user", item.path("user").path("login").asText());
		result.put("labels", GitHubService.labelNames(item));
		return result;
	}

	private List<Map<String, Object>> items(GitHubService.Fetch result) {
		List<Map<String, Object>> items = new ArrayList<>();
		if (result != null && result.data().path("items").isArray()) {
			for (JsonNode item : result.data().path("items")) if (item.isObject()) items.add(summary(item));
		}
		return items;
	}

	private static Integer total(GitHubService.Fetch result, List<Map<String, Object>> problems) {
		if (result == null) return null;
		if (!result.data().path("total_count").canConvertToInt()) {
			problems.add(GitHubService.malformed(result, "GitHub search returned no total_count").error());
			return null;
		}
		if (result.data().path("incomplete_results").asBoolean()) {
			problems.add(Map.of("error", "bounded_results", "message", "GitHub reported incomplete search results", "source", result.source()));
		}
		return result.data().path("total_count").asInt();
	}

	private static int pages(Integer pages) {
		int count = pages == null ? 1 : pages;
		if (count < 1 || count > MAX_PAGES) throw ApiSupport.badRequest("pages must be between 1 and " + MAX_PAGES);
		return count;
	}

	private static LocalDate date(String value, String label) {
		if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) throw ApiSupport.badRequest(label + " must be a date formatted as YYYY-MM-DD");
		try {
			return LocalDate.parse(value);
		} catch (DateTimeParseException e) {
			throw ApiSupport.badRequest(label + " is not a valid date");
		}
	}

	private static long days(String timestamp, Instant now) {
		try {
			return Math.max(0, Duration.between(Instant.parse(timestamp), now).toDays());
		} catch (DateTimeParseException e) {
			return -1;
		}
	}
}
