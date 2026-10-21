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
package se.hirt.openjdk.helper.mcp;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import se.hirt.openjdk.helper.core.OpenJDKHelperService;
import se.hirt.openjdk.helper.common.ToolResults;



/** The same read-only operations are available through REST and both MCP transports. */
@ApplicationScoped
public class OpenJDKHelperTools {
	@Inject
	OpenJDKHelperService helperService;

	@Inject
	ToolResults results;

	@Tool(description = "Get the version of the OpenJDK Helper service.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public ToolResponse getVersion() {
		return ToolResponse.success(new TextContent(helperService.getVersion()));
	}

	@Tool(description = "Search the OpenJDK census by a regular expression matching a person's name or username.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse searchPeople(
			@ToolArg(description = "Name or username search expression, for example Hirt or Hir.*") String query) {
		return results.call(() -> helperService.getCensusService().searchPeople(query));
	}

	@Tool(description = "Get information about one or more people in the OpenJDK census by their IDs.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getPerson(
			@ToolArg(description = "The person's ID or username, or a comma-separated list of IDs") String userid) {
		return results.call(() -> helperService.getCensusService().getPeople(userid));
	}

	@Tool(description = "Search OpenJDK census projects using a regular expression.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse searchProjects(
			@ToolArg(description = "The search expression for projects") String query) {
		return results.call(() -> helperService.getCensusService().searchProjects(query));
	}

	@Tool(description = "Get information about a project in the OpenJDK census by its ID.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getProject(@ToolArg(description = "The project ID") String projectid) {
		return results.call(() -> helperService.getCensusService().getProjects(projectid));
	}

	@Tool(description = "Search OpenJDK census groups using a regular expression.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse searchGroups(@ToolArg(description = "The search expression for groups") String query) {
		return results.call(() -> helperService.getCensusService().searchGroups(query));
	}

	@Tool(description = "Get information about a group in the OpenJDK census by its ID.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getGroup(@ToolArg(description = "The group ID") String groupid) {
		return results.call(() -> helperService.getCensusService().getGroups(groupid));
	}

	@Tool(description = "Get public GitHub repositories for a user.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getGitHubRepos(
			@ToolArg(description = "The GitHub username or organization") String user,
			@ToolArg(description = "Page size, 1 to 100; default 30", required = false) Integer limit,
			@ToolArg(description = "Page number starting at 1", required = false) Integer page) {
		return results.call(() -> helperService.getGitHubService().listRepositories(user, limit, page));
	}

	@Tool(description = "List GitHub pull requests with bounded pagination and optional author, state and sort filters. Defaults to open requests.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getGitHubPulls(
			@ToolArg(description = "The repository owner") String owner,
			@ToolArg(description = "The repository name") String repo,
			@ToolArg(description = "Optional GitHub author username", required = false) String user,
			@ToolArg(description = "open, closed or all; default open", required = false) String state,
			@ToolArg(description = "created, updated, popularity or long-running; default created", required = false) String sort,
			@ToolArg(description = "asc or desc; default desc", required = false) String direction,
			@ToolArg(description = "Page size, 1 to 100; default 30", required = false) Integer limit,
			@ToolArg(description = "Upstream page number starting at 1; inspect hasMore/nextPage when filtering authors", required = false) Integer page) {
		return results.call(() -> helperService.getGitHubService().listPullRequests(owner, repo, user, state, sort, direction, limit, page));
	}
}
