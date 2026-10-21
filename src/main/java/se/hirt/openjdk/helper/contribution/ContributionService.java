/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.contribution;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import se.hirt.openjdk.helper.census.CensusService;
import se.hirt.openjdk.helper.common.ApiSupport;
import se.hirt.openjdk.helper.common.UpstreamException;
import se.hirt.openjdk.helper.common.UpstreamHttp;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Contributor context derived from the requested repository ref, with source evidence. */
@ApplicationScoped
public class ContributionService {
	@Inject
	UpstreamHttp http;
	@Inject
	CensusService census;
	@ConfigProperty(name = "openjdk.github.raw-base-url", defaultValue = "https://raw.githubusercontent.com")
	String rawBaseUrl = "https://raw.githubusercontent.com";
	@ConfigProperty(name = "openjdk.github.token")
	Optional<String> token = Optional.empty();

	public Map<String, Object> getContributionContext(String owner, String repo, String ref) {
		Repository repository = repository(owner, repo, ref);
		Source config = fetch(repository, ".jcheck/conf");
		Map<String, Map<String, String>> sections = parseConfig(config.response().body());
		Map<String, String> general = sections.getOrDefault("general", Map.of());
		String project = general.get("project");
		Map<String, Object> result = repositoryResult(repository);
		result.put("source", sourceMetadata(config));
		result.put("projectId", project);
		result.put("jbsProject", general.get("jbs"));
		result.put("version", general.get("version"));
		result.put("reviewRules", sections.getOrDefault("checks \"reviewers\"", Map.of()));
		result.put("committerRules", sections.getOrDefault("checks \"committer\"", Map.of()));
		result.put("checks", sections.getOrDefault("checks", Map.of()));
		result.put("ruleSemantics",
				"Values are declared in this ref's .jcheck/conf; absent values are unspecified here. Bot configuration and project policy may add requirements.");
		if (project != null && !project.isBlank()) {
			result.put("censusUrl", "https://openjdk.org/census#" + ApiSupport.segment(project));
			try (Response members = census.getProjectMembers(project, "lead,reviewer,committer", null, 30, 0)) {
				result.put("censusMembers", members.getEntity());
				result.put("censusStatus", members.getStatus());
			} catch (WebApplicationException e) {
				result.put("censusStatus", e.getResponse().getStatus());
				result.put("censusMembers", Map.of("available", false, "metadata", census.getCensusStatus()));
			}
		}
		result.put("memberSemantics",
				"Listed people have exact census roles, not inferred subject expertise or review availability. Use getProjectMembers to filter or continue the list.");
		return result;
	}

	public Map<String, Object> getBuildGuidance(String owner, String repo, String ref, String topic, String test) {
		Repository repository = repository(owner, repo, ref);
		String searchTopic = boundedQuery(topic, "topic");
		String testQuery = boundedQuery(test, "test");
		Map<String, Object> result = repositoryResult(repository);
		result.put("topic", searchTopic);
		result.put("test", testQuery);
		List<Map<String, Object>> documents = new ArrayList<>();
		for (String path : List.of("doc/building.md", "doc/testing.md")) {
			try {
				Source source = fetch(repository, path);
				Map<String, Object> document = sourceMetadata(source);
				document.put("available", true);
				List<Map<String, Object>> excerpts = excerpts(source.response().body(), searchTopic);
				document.put("excerpts", excerpts);
				document.put("matched", !excerpts.isEmpty());
				document.put("excerptCharacterLimit", 6000);
				document.put("completeDocument", false);
				documents.add(document);
			} catch (UpstreamException e) {
				if (e.status() != 404)
					throw e;
				documents.add(missingSource(repository, path));
			}
		}
		result.put("documents", documents);
		if (!testQuery.isEmpty() || normalize(searchTopic).contains("problem")) {
			result.put("problemLists", problemLists(repository, testQuery));
		}
		result.put("guidanceSemantics",
				"Excerpts are verbatim repository documentation for the requested ref. Commands are not synthesized or executed; verify placeholders and platform prerequisites in the linked source.");
		return result;
	}

	private Map<String, Object> problemLists(Repository repository, String test) {
		List<String> paths = List.of("test/jdk/ProblemList.txt", "test/langtools/ProblemList.txt",
				"test/hotspot/jtreg/ProblemList.txt", "test/jaxp/ProblemList.txt", "test/lib-test/ProblemList.txt");
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("pathSelection", "Standard JDK candidate paths");
		try {
			Source source = fetch(repository, ".jcheck/conf");
			String dirs = parseConfig(source.response().body()).getOrDefault("checks \"problemlists\"", Map.of())
					.get("dirs");
			if (dirs != null && !dirs.isBlank()) {
				paths = Arrays.stream(dirs.split(",")).map(String::strip).filter(ContributionService::safeRelativePath)
						.limit(8).map(dir -> dir + "/ProblemList.txt").toList();
				result.put("pathSelection",
						"Directories declared by .jcheck/conf checks.problemlists.dirs (at most eight)");
				result.put("configurationSource", sourceMetadata(source));
			}
		} catch (UpstreamException e) {
			if (e.status() != 404)
				throw e;
		}
		List<Map<String, Object>> lists = new ArrayList<>();
		for (String path : paths) {
			try {
				Source source = fetch(repository, path);
				Map<String, Object> list = sourceMetadata(source);
				list.put("available", true);
				String directory = path.substring(0, path.lastIndexOf('/')) + "/";
				String relativeTest = test.startsWith(directory) ? test.substring(directory.length()) : test;
				List<Map<String, Object>> matches = new ArrayList<>();
				String[] lines = source.response().body().split("\\R", -1);
				int matchingLines = 0;
				for (int i = 0; i < lines.length; i++) {
					String line = lines[i].strip();
					if (line.isEmpty() || line.startsWith("#"))
						continue;
					String[] fields = line.split("\\s+", 4);
					if (!relativeTest.isEmpty() && !fields[0].contains(relativeTest))
						continue;
					matchingLines++;
					if (matches.size() >= 20)
						continue;
					Map<String, Object> entry = new LinkedHashMap<>();
					entry.put("line", i + 1);
					entry.put("text", line.substring(0, Math.min(line.length(), 1000)));
					entry.put("test", fields[0]);
					if (fields.length > 1)
						entry.put("issues", fields[1]);
					if (fields.length > 2)
						entry.put("platforms", fields[2]);
					matches.add(entry);
				}
				list.put("results", matches);
				list.put("total", matchingLines);
				list.put("truncated", matchingLines > matches.size());
				lists.add(list);
			} catch (UpstreamException e) {
				if (e.status() != 404)
					throw e;
				lists.add(missingSource(repository, path));
			}
		}
		result.put("files", lists);
		result.put("coverage",
				"Literal test-path matching in the listed ProblemList.txt files only. Variants, wildcard exclusions, platform-specific conditions and other files may apply; no match does not prove a test is enabled.");
		return result;
	}

	static Map<String, Map<String, String>> parseConfig(String text) {
		Map<String, Map<String, String>> sections = new LinkedHashMap<>();
		Map<String, String> current = null;
		for (String raw : text.split("\\R")) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#") || line.startsWith(";"))
				continue;
			if (line.startsWith("[") && line.endsWith("]")) {
				String section = line.substring(1, line.length() - 1).strip().toLowerCase(Locale.ROOT);
				current = sections.computeIfAbsent(section, ignored -> new LinkedHashMap<>());
			} else if (current != null && line.contains("=")) {
				int equals = line.indexOf('=');
				String key = line.substring(0, equals).strip().toLowerCase(Locale.ROOT);
				String value = line.substring(equals + 1).strip();
				current.put(key, value);
			}
		}
		return sections;
	}

	static List<Map<String, Object>> excerpts(String content, String query) {
		String normalized = normalize(query);
		if (normalized.equals("build") || normalized.equals("building") || normalized.equals("test")
				|| normalized.equals("testing"))
			normalized = "";
		String[] lines = content.split("\\R", -1);
		List<Map<String, Object>> result = new ArrayList<>();
		int previousEnd = -1;
		int remaining = 6000;
		for (int i = 0; i < lines.length && result.size() < 3 && remaining > 0; i++) {
			if (!normalized.isEmpty() && !normalize(lines[i]).contains(normalized))
				continue;
			if (i <= previousEnd)
				continue;
			int start = Math.max(previousEnd + 1, Math.max(0, i - 3));
			int end = Math.min(lines.length, i + (normalized.isEmpty() ? 45 : 24));
			StringBuilder text = new StringBuilder();
			int lastLine = start;
			boolean clipped = false;
			int budget = Math.min(remaining, normalized.isEmpty() ? 6000 : 2400);
			for (int index = start; index < end; index++) {
				String line = lines[index] + (index + 1 < end ? "\n" : "");
				if (text.length() + line.length() > budget) {
					text.append(line, 0, Math.max(0, budget - text.length()));
					lastLine = index;
					clipped = true;
					break;
				}
				text.append(line);
				lastLine = index;
			}
			result.add(Map.of("startLine", start + 1, "endLine", lastLine + 1, "text", text.toString(), "clipped",
					clipped));
			remaining -= text.length();
			previousEnd = lastLine;
			if (normalized.isEmpty())
				break;
		}
		return result;
	}

	private Source fetch(Repository repository, String path) {
		URI uri = rawUri(repository, path);
		return new Source(path, uri, http.get(uri, token.orElse(null)));
	}

	private URI rawUri(Repository repository, String path) {
		return ApiSupport.uri(rawBaseUrl, ApiSupport.segment(repository.owner()) + "/"
				+ ApiSupport.segment(repository.repo()) + "/" + ApiSupport.segment(repository.ref()) + "/" + path,
				Map.of());
	}

	private static Map<String, Object> sourceMetadata(Source source) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("path", source.path());
		result.put("url", source.uri().toString());
		result.put("fetchedAt", source.response().fetchedAt().toString());
		result.put("cached", source.response().cached());
		result.put("status", source.response().status());
		return result;
	}

	private Map<String, Object> missingSource(Repository repository, String path) {
		return Map.of("path", path, "url", rawUri(repository, path).toString(), "available", false, "status", 404);
	}

	private static Map<String, Object> repositoryResult(Repository repository) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("owner", repository.owner());
		result.put("repo", repository.repo());
		result.put("ref", repository.ref());
		result.put("repositoryUrl", "https://github.com/" + repository.owner() + "/" + repository.repo());
		return result;
	}

	private static Repository repository(String owner, String repo, String ref) {
		owner = ApiSupport.requireName(owner, "owner");
		repo = ApiSupport.requireName(repo, "repo");
		// HEAD explicitly means the repository's default branch; callers can request a branch, tag or SHA.
		ref = ref == null || ref.isBlank() ? "HEAD" : ref.strip();
		if (ref.length() > 200 || !ref.matches("[A-Za-z0-9_./-]+") || ref.contains("..") || ref.contains("//")
				|| ref.startsWith("/") || ref.endsWith("/")) {
			throw ApiSupport.badRequest(
					"ref must be a branch, tag or commit SHA using letters, numbers, dots, underscores, hyphens or slashes");
		}
		return new Repository(owner, repo, ref);
	}

	private static String boundedQuery(String query, String label) {
		String value = query == null ? "" : query.strip();
		if (value.length() > 200 || value.chars().anyMatch(Character::isISOControl)) {
			throw ApiSupport.badRequest(label + " must contain at most 200 printable characters");
		}
		return value;
	}

	private static String normalize(String value) {
		return value.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ');
	}

	private static boolean safeRelativePath(String value) {
		return value.matches("[A-Za-z0-9_./-]{1,200}") && !value.contains("..") && !value.startsWith("/")
				&& !value.endsWith("/");
	}

	private record Repository(String owner, String repo, String ref) {
	}

	private record Source(String path, URI uri, UpstreamHttp.FetchResult response) {
	}
}
