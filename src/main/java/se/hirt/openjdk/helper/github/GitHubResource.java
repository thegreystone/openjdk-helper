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

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import java.util.Map;

@Path("/github")
public class GitHubResource {
	@Inject
	GitHubService githubService;

	@GET
	@Path("/repos")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getRepositories(
			@QueryParam("user") @Parameter(description = "The user for which to list the repositories.", example = "thegreystone")
			String user,
			@QueryParam("limit") @Parameter(description = "Items per page, 1 to 100; defaults to 30") Integer limit,
			@QueryParam("page") @Parameter(description = "Page number; defaults to 1. Follow X-Next-Page when X-Has-More is true") Integer page) {
		return githubService.getRepositories(user, limit, page);
	}

	@GET
	@Path("/pulls")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPullRequests(
			@QueryParam("owner") @Parameter(description = "The owner of the repository", example = "openjdk", required = true) String owner,
			@QueryParam("repo") @Parameter(description = "The name of the repository", example = "jmc", required = true) String repo,
			@QueryParam("user") @Parameter(description = "The user opening the PR (optional)", example = "thegreystone") String user,
			@QueryParam("state") @Parameter(description = "The state of the PRs to list, [open|closed|all]", example = "open")
			@DefaultValue("open") String state, @QueryParam("sort")
	@Parameter(description = "In what order to get the results, [created|updated|popularity|long-running]", example = "created")
	@DefaultValue("created") String sort,
			@QueryParam("direction") @Parameter(description = "The sort order, [asc|desc]", example = "desc") @DefaultValue("desc")
			String direction, @QueryParam("maxresults")
	@Parameter(description = "Upstream page size, 1 to 100; defaults to 30. Legacy -1 selects 100. Author filtering applies within the page; follow X-Next-Page even for an empty result.", example = "30")
	@DefaultValue("30") int maxResults,
			@QueryParam("page") @Parameter(description = "Page number; defaults to 1") Integer page) {
		return githubService.getPullRequests(owner, repo, user, state, sort, direction, maxResults, page);
	}

	@GET
	@Path("/review")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> getReviewContext(
			@QueryParam("owner") @DefaultValue("openjdk") String owner,
			@QueryParam("repo") @Parameter(required = true, description = "Repository name", example = "jdk") String repo,
			@QueryParam("number") @Parameter(required = true, description = "Pull request number") int number) {
		return githubService.getReviewContext(owner, repo, number);
	}

	@GET
	@Path("/backports")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> getBackportStatus(
			@QueryParam("owner") @DefaultValue("openjdk") String owner,
			@QueryParam("repo") @Parameter(required = true, description = "Original repository", example = "jdk") String repo,
			@QueryParam("commit") @Parameter(required = true, description = "Original commit SHA, at least seven hexadecimal digits") String commit,
			@QueryParam("targets") @Parameter(required = true, description = "Up to five comma-separated owner/repo or owner/repo@branch targets", example = "openjdk/jdk21u,openjdk/jdk17u@master") String targets,
			@QueryParam("limit") @Parameter(description = "Search page size per target, 1 to 100; defaults to 30") Integer limit) {
		return githubService.getBackportStatus(owner, repo, commit, targets, limit);
	}

	@GET
	@Path("/triage")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> getPullRequestTriage(
			@QueryParam("owner") @DefaultValue("openjdk") String owner,
			@QueryParam("repo") @Parameter(required = true, description = "Repository name", example = "jmc") String repo,
			@QueryParam("pages") @Parameter(description = "Pages of 100 open pull requests to scan, 1 to 3; defaults to 1") Integer pages,
			@QueryParam("staleDays") @Parameter(description = "Idle days after which a pull request is stale, 1 to 365; defaults to 30") Integer staleDays) {
		return githubService.getPullRequestTriage(owner, repo, pages, staleDays);
	}

	@GET
	@Path("/contributor")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> getContributorRecord(
			@QueryParam("owner") @DefaultValue("openjdk") String owner,
			@QueryParam("repo") @Parameter(required = true, description = "Repository name", example = "jmc") String repo,
			@QueryParam("login") @Parameter(required = true, description = "GitHub login of the contributor", example = "thegreystone") String login,
			@QueryParam("limit") @Parameter(description = "Examples to list from each end, 1 to 50; defaults to 10") Integer limit) {
		return githubService.getContributorRecord(owner, repo, login, limit);
	}

	@GET
	@Path("/activity")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> getProjectActivity(
			@QueryParam("owner") @DefaultValue("openjdk") String owner,
			@QueryParam("repo") @Parameter(required = true, description = "Repository name", example = "jmc") String repo,
			@QueryParam("since") @Parameter(required = true, description = "Start date (inclusive), YYYY-MM-DD", example = "2026-07-01") String since,
			@QueryParam("until") @Parameter(description = "End date (inclusive), YYYY-MM-DD; defaults to today") String until,
			@QueryParam("pages") @Parameter(description = "Pages of 100 integrated pull requests to scan, 1 to 3; defaults to 1") Integer pages) {
		return githubService.getProjectActivity(owner, repo, since, until, pages);
	}
}
