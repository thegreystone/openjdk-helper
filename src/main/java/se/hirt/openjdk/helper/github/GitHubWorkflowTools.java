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

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.hirt.openjdk.helper.common.ToolResults;

@ApplicationScoped
public class GitHubWorkflowTools {
	@Inject GitHubService github;
	@Inject ToolResults results;

	@Tool(description = "Inspect an OpenJDK pull request: full description, Skara progress, JBS links, reviews and checks at its current head. Reports partial data and source timestamps; does not infer integration approval.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getOpenJdkReview(
			@ToolArg(description = "Repository owner", defaultValue = "openjdk") String owner,
			@ToolArg(description = "Repository name, for example jdk, jmc or jfx") String repo,
			@ToolArg(description = "Pull request number") int number) {
		return results.call(() -> github.getReviewContext(owner, repo, number));
	}

	@Tool(description = "Find backport evidence for an original commit in specified OpenJDK update repositories/branches. Matches commit provenance and Backport-of trailers, not just issue numbers. A bounded search miss is not proof of absence.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getBackportStatus(
			@ToolArg(description = "Original repository owner", defaultValue = "openjdk") String owner,
			@ToolArg(description = "Original repository name, for example jdk") String repo,
			@ToolArg(description = "Original commit SHA, at least seven hexadecimal digits") String commit,
			@ToolArg(description = "Up to five comma-separated owner/repo or owner/repo@branch targets, for example openjdk/jdk21u,openjdk/jdk17u@master") String targets,
			@ToolArg(description = "Search page size per target, 1 to 100; defaults to 30", required = false) Integer limit) {
		return results.call(() -> github.getBackportStatus(owner, repo, commit, targets, limit));
	}

	@Tool(description = "Triage open pull requests of a repository by Skara state: ready to integrate, awaiting review (rfr), blocked by Skara errors, CSR pending, merge conflicts, stale, drafts and unlabeled. Bounded scan with counts and sample items per group.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getPullRequestTriage(
			@ToolArg(description = "Repository owner", defaultValue = "openjdk") String owner,
			@ToolArg(description = "Repository name, for example jdk, jmc or jfx") String repo,
			@ToolArg(description = "Pages of 100 open pull requests to scan, 1 to 3; default 1", required = false) Integer pages,
			@ToolArg(description = "Days without GitHub updates after which a pull request counts as stale, 1 to 365; default 30", required = false) Integer staleDays) {
		return results.call(() -> github.getPullRequestTriage(owner, repo, pages, staleDays));
	}

	@Tool(description = "Summarize one GitHub user's pull requests in a repository: integrated count with earliest and latest examples, open and closed-but-not-integrated counts. Supports Author, Committer and Reviewer nominations; does not judge significance.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getContributorRecord(
			@ToolArg(description = "Repository owner", defaultValue = "openjdk") String owner,
			@ToolArg(description = "Repository name, for example jmc") String repo,
			@ToolArg(description = "GitHub login of the contributor") String login,
			@ToolArg(description = "Examples to list from each end, 1 to 50; default 10", required = false) Integer limit) {
		return results.call(() -> github.getContributorRecord(owner, repo, login, limit));
	}

	@Tool(description = "Summarize repository activity in a period for reports: integrated pull requests, authors ranked by integrations, opened pull requests and first-time contributors. Dates are YYYY-MM-DD; the period must not exceed 366 days.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getProjectActivity(
			@ToolArg(description = "Repository owner", defaultValue = "openjdk") String owner,
			@ToolArg(description = "Repository name, for example jmc") String repo,
			@ToolArg(description = "Start date (inclusive), for example 2026-07-01") String since,
			@ToolArg(description = "End date (inclusive); defaults to today", required = false) String until,
			@ToolArg(description = "Pages of 100 integrated pull requests to scan for authors, 1 to 3; default 1", required = false) Integer pages) {
		return results.call(() -> github.getProjectActivity(owner, repo, since, until, pages));
	}
}
