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
import se.hirt.openjdk.helper.common.ApiSupport;
import se.hirt.openjdk.helper.common.UpstreamException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Searches for provenance, never inferring a backport merely from a shared JBS issue number. */
final class BackportLookup {
	private static final Pattern TRAILER = Pattern.compile("(?im)^Backport-of:\\s*([0-9a-f]{40})\\s*$");
	private static final int CANDIDATE_CHECKS = 3;
	private final GitHubService github;

	BackportLookup(GitHubService github) {
		this.github = github;
	}

	Map<String, Object> lookup(String owner, String repo, String commit, String targets, Integer limit) {
		String repository = GitHubService.repository(owner, repo);
		if (commit == null || !commit.matches("[0-9a-fA-F]{7,40}"))
			throw ApiSupport.badRequest("commit must be a 7 to 40 digit hexadecimal commit ID");
		int size = ApiSupport.limit(limit);
		List<Target> destinations = targets(targets);
		var origin = github.fetch("repos/" + repository + "/commits/" + commit, Map.of());
		String sha = origin.data().path("sha").asText();
		if (!sha.matches("[0-9a-fA-F]{40}"))
			throw GitHubService.malformed(origin, "Source commit has no valid SHA");
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("sourceCommit", github.fields(origin.data(), "sha", "html_url", "commit"));
		response.put("sourceRepository", owner + "/" + repo);
		response.put("source", origin.source());
		response.put("issues", GitHubService.issues(origin.data().path("commit").path("message").asText()));
		List<Map<String, Object>> results = new ArrayList<>();
		for (Target target : destinations)
			results.add(checkTarget(target, sha, size));
		response.put("targets", results);
		response.put("searchLimit", size);
		response.put("interpretation",
				"Found means commit or pull-request evidence was found, not that the change remains effective after later reverts. not_found_in_search is not proof of absence; searches are bounded and GitHub indexing may lag. "
						+ "Skara integrates by pushing commits and closing pull requests, so merged is always false; a backport pull request is integrated when its integrated flag is true, and open or unlabeled closed pull requests are not.");
		return response;
	}

	private Map<String, Object> checkTarget(Target target, String sourceSha, int limit) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("repository", target.repository());
		List<Map<String, Object>> sources = new ArrayList<>();
		List<Map<String, Object>> problems = new ArrayList<>();
		List<Map<String, Object>> evidence = new ArrayList<>();
		result.put("sources", sources);
		result.put("problems", problems);
		result.put("evidence", evidence);
		String branch = target.branch();
		if (branch == null) {
			var metadata = github.optional("repos/" + target.repository(), Map.of(), sources, problems);
			if (metadata == null)
				return unknown(result);
			branch = metadata.data().path("default_branch").asText();
		}
		result.put("branch", branch);
		if (branch.isBlank()) {
			problems.add(Map.of("error", "missing_default_branch"));
			return unknown(result);
		}
		var targetBranch = github.optional("repos/" + target.repository() + "/branches/" + ApiSupport.segment(branch),
				Map.of(), sources, problems);
		if (targetBranch == null)
			return unknown(result);
		String targetHead = targetBranch.data().path("commit").path("sha").asText();
		if (!targetHead.matches("[0-9a-fA-F]{40}")) {
			problems.add(Map.of("error", "invalid_branch_head", "source", targetBranch.source()));
			return unknown(result);
		}
		result.put("targetHeadSha", targetHead);
		if (ancestor(target.repository(), sourceSha, targetHead, sources, problems, true)) {
			evidence.add(Map.of("kind", "original_commit_in_target_history", "sha", sourceSha, "url",
					"https://github.com/" + target.repository() + "/commit/" + sourceSha));
			result.put("status", "found");
			result.put("partial", !problems.isEmpty());
			return result;
		}

		var commits = github.optional("search/commits",
				Map.of("q", "repo:" + target.repository() + " " + sourceSha, "per_page", limit), sources, problems);
		int checked = 0;
		if (GitHubService.hasArray(commits, "items", problems)) {
			GitHubService.markPagination(commits, commits.data().path("items").size(), problems);
			for (JsonNode candidate : commits.data().path("items")) {
				if (!hasTrailer(candidate.path("commit").path("message").asText(), sourceSha))
					continue;
				if (checked++ >= CANDIDATE_CHECKS) {
					problems.add(Map.of("error", "candidate_limit", "message",
							"More backport commits require branch verification"));
					break;
				}
				String sha = candidate.path("sha").asText();
				if (!sha.matches("[0-9a-fA-F]{40}")) {
					problems.add(
							GitHubService.malformed(commits, "Backport commit candidate has no valid SHA").error());
					continue;
				}
				if (ancestor(target.repository(), sha, targetHead, sources, problems, false))
					evidence.add(Map.of("kind", "backport_commit", "sha", sha, "matchedOn", "Backport-of trailer",
							"sourceSha", sourceSha, "url", candidate.path("html_url").asText()));
			}
		}
		var pulls = github.optional("search/issues",
				Map.of("q", "repo:" + target.repository() + " is:pr " + sourceSha, "per_page", limit), sources,
				problems);
		checked = 0;
		if (GitHubService.hasArray(pulls, "items", problems)) {
			GitHubService.markPagination(pulls, pulls.data().path("items").size(), problems);
			for (JsonNode item : pulls.data().path("items")) {
				if (!referencesBackport(item.path("title").asText(), item.path("body").asText(), sourceSha))
					continue;
				if (checked++ >= CANDIDATE_CHECKS) {
					problems.add(Map.of("error", "candidate_limit", "message",
							"More backport pull requests require verification"));
					break;
				}
				int number = item.path("number").asInt();
				if (number < 1) {
					problems.add(GitHubService.malformed(pulls, "Backport pull request candidate has no valid number")
							.error());
					continue;
				}
				var pr = github.optional("repos/" + target.repository() + "/pulls/" + number, Map.of(), sources,
						problems);
				if (pr == null)
					continue;
				if (pr.data().path("base").path("ref").asText().isBlank()) {
					problems.add(GitHubService.malformed(pr, "Backport pull request has no target branch").error());
					continue;
				}
				if (!branch.equals(pr.data().path("base").path("ref").asText())
						|| !referencesBackport(pr.data().path("title").asText(), pr.data().path("body").asText(),
								sourceSha))
					continue;
				Map<String, Object> match = github.fields(pr.data(), "number", "title", "html_url", "state", "merged",
						"merged_at");
				// Skara never merges through GitHub: the integrated label is the integration signal.
				match.put("labels", GitHubService.labelNames(pr.data()));
				match.put("integrated", GitHubService.hasLabel(pr.data(), "integrated"));
				match.put("kind", "backport_pull_request");
				match.put("matchedOn", "original commit referenced in backport title/body");
				match.put("sourceSha", sourceSha);
				evidence.add(match);
			}
		}
		result.put("status", !evidence.isEmpty() ? "found" : !problems.isEmpty() ? "partial" : "not_found_in_search");
		result.put("partial", !problems.isEmpty());
		result.put("candidateVerificationLimit", CANDIDATE_CHECKS);
		return result;
	}

	private boolean ancestor(
		String repository, String sha, String targetHead, List<Map<String, Object>> sources,
		List<Map<String, Object>> problems, boolean allowMissingSource) {
		try {
			var comparison = github.fetch("repos/" + repository + "/compare/" + sha + "..." + targetHead,
					Map.of("per_page", 1));
			sources.add(comparison.source());
			String status = comparison.data().path("status").asText();
			if (!Set.of("ahead", "behind", "diverged", "identical").contains(status))
				throw GitHubService.malformed(comparison, "GitHub returned no valid commit comparison status");
			return Set.of("ahead", "identical").contains(status);
		} catch (UpstreamException e) {
			if (!allowMissingSource || e.status() != 404)
				problems.add(e.error());
			return false;
		}
	}

	private static Map<String, Object> unknown(Map<String, Object> result) {
		result.put("status", "unknown");
		result.put("partial", true);
		return result;
	}

	private static boolean hasTrailer(String message, String sourceSha) {
		var matcher = TRAILER.matcher(message);
		while (matcher.find())
			if (matcher.group(1).equalsIgnoreCase(sourceSha))
				return true;
		return false;
	}

	private static boolean referencesBackport(String title, String body, String sha) {
		if (hasTrailer(body, sha))
			return true;
		return Pattern.compile("(?is)\\bbackport\\b.{0,180}(?<![0-9a-f])" + Pattern.quote(sha) + "(?![0-9a-f])")
				.matcher(title + "\n" + body).find();
	}

	private static List<Target> targets(String value) {
		if (value == null || value.isBlank())
			throw ApiSupport.badRequest("targets is required: comma-separated owner/repo or owner/repo@branch");
		String[] entries = value.split(",", -1);
		if (entries.length > 5)
			throw ApiSupport.badRequest("At most five backport targets can be checked per request");
		List<Target> targets = new ArrayList<>();
		for (String entry : entries) {
			String[] target = entry.trim().split("@", 2);
			String[] names = target[0].split("/", -1);
			if (names.length != 2)
				throw ApiSupport.badRequest("Each target must be owner/repo, optionally followed by @branch");
			String branch = target.length == 2 ? target[1] : null;
			if (branch != null && (branch.isBlank() || branch.length() > 200 || branch.contains("..")
					|| !branch.matches("[A-Za-z0-9_./-]+")))
				throw ApiSupport.badRequest("Invalid target branch");
			targets.add(new Target(GitHubService.repository(names[0], names[1]), branch));
		}
		return targets;
	}

	private record Target(String repository, String branch) {
	}
}
