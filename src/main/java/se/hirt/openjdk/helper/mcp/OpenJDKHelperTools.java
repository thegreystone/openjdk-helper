/*
 * Copyright (C) 2025 Marcus Hirt
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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import se.hirt.openjdk.helper.census.CensusService;
import se.hirt.openjdk.helper.core.OpenJDKHelperService;
import se.hirt.openjdk.helper.github.GitHubService;

import java.util.List;

/**
 * MCP Tools for OpenJDK Helper using the official Quarkiverse MCP Server extension.
 */
@ApplicationScoped
public class OpenJDKHelperTools {
	private static final Logger LOG = Logger.getLogger(OpenJDKHelperTools.class);
	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Inject
	OpenJDKHelperService helperService;

	/**
	 * Get the version of the OpenJDK Helper service.
	 */
	@Tool(description = "Get the version of the OpenJDK Helper service.")
	public ToolResponse getVersion() {
		LOG.info("MCP tool: Getting version information");
		return ToolResponse.success(new TextContent(helperService.getVersion()));
	}

	/**
	 * Search for people in the OpenJDK census.
	 */
	@Tool(description = "Search for people in the OpenJDK census by name or username.")
	public ToolResponse searchPeople(
			@ToolArg(description = "The search query, such as a name or username") String query) {
		try {
			LOG.info("MCP tool: Searching for people matching: " + query);
			CensusService censusService = helperService.getCensusService();
			Response response = censusService.searchPeople(query);

			if (response.getStatus() != Response.Status.OK.getStatusCode()) {
				LOG.error("Error searching people: " + response.getEntity().toString());
				return new ToolResponse(true, List.of(new TextContent("Error searching people: " + response.getEntity().toString())));
			}

			return ToolResponse.success(new TextContent(response.getEntity().toString()));
		} catch (Exception e) {
			LOG.error("Error in searchPeople", e);
			return new ToolResponse(true, List.of(new TextContent("Error searching people: " + e.getMessage())));
		}
	}

	/**
	 * Get a person from the OpenJDK census by their ID.
	 */
	@Tool(description = "Get information about a person in the OpenJDK census by their ID.")
	public ToolResponse getPerson(
			@ToolArg(description = "The person's ID or username") String userid) {
		try {
			LOG.info("MCP tool: Getting person with ID: " + userid);
			CensusService censusService = helperService.getCensusService();
			Response response = censusService.getPeople(userid);

			if (response.getStatus() != Response.Status.OK.getStatusCode()) {
				LOG.error("Error fetching person: " + response.getEntity().toString());
				return new ToolResponse(true, List.of(new TextContent("Error fetching person: " + response.getEntity().toString())));
			}

			return ToolResponse.success(new TextContent(response.getEntity().toString()));
		} catch (Exception e) {
			LOG.error("Error in getPerson", e);
			return new ToolResponse(true, List.of(new TextContent("Error fetching person: " + e.getMessage())));
		}
	}

	/**
	 * Search for projects in the OpenJDK census.
	 */
	@Tool(description = "Search for projects in the OpenJDK census.")
	public ToolResponse searchProjects(
			@ToolArg(description = "The search query for projects") String query) {
		try {
			LOG.info("MCP tool: Searching for projects matching: " + query);
			CensusService censusService = helperService.getCensusService();
			Response response = censusService.searchProjects(query);

			if (response.getStatus() != Response.Status.OK.getStatusCode()) {
				LOG.error("Error searching projects: " + response.getEntity().toString());
				return new ToolResponse(true, List.of(new TextContent("Error searching projects: " + response.getEntity().toString())));
			}

			return ToolResponse.success(new TextContent(response.getEntity().toString()));
		} catch (Exception e) {
			LOG.error("Error in searchProjects", e);
			return new ToolResponse(true, List.of(new TextContent("Error searching projects: " + e.getMessage())));
		}
	}

	/**
	 * Get a project from the OpenJDK census by its ID.
	 */
	@Tool(description = "Get information about a project in the OpenJDK census by its ID.")
	public ToolResponse getProject(
			@ToolArg(description = "The project ID") String projectid) {
		try {
			LOG.info("MCP tool: Getting project with ID: " + projectid);
			CensusService censusService = helperService.getCensusService();
			Response response = censusService.getProjects(projectid);

			if (response.getStatus() != Response.Status.OK.getStatusCode()) {
				LOG.error("Error fetching project: " + response.getEntity().toString());
				return new ToolResponse(true, List.of(new TextContent("Error fetching project: " + response.getEntity().toString())));
			}

			return ToolResponse.success(new TextContent(response.getEntity().toString()));
		} catch (Exception e) {
			LOG.error("Error in getProject", e);
			return new ToolResponse(true, List.of(new TextContent("Error fetching project: " + e.getMessage())));
		}
	}

	/**
	 * Search for groups in the OpenJDK census.
	 */
	@Tool(description = "Search for groups in the OpenJDK census.")
	public ToolResponse searchGroups(
			@ToolArg(description = "The search query for groups") String query) {
		try {
			LOG.info("MCP tool: Searching for groups matching: " + query);
			CensusService censusService = helperService.getCensusService();
			Response response = censusService.searchGroups(query);

			if (response.getStatus() != Response.Status.OK.getStatusCode()) {
				LOG.error("Error searching groups: " + response.getEntity().toString());
				return new ToolResponse(true, List.of(new TextContent("Error searching groups: " + response.getEntity().toString())));
			}

			return ToolResponse.success(new TextContent(response.getEntity().toString()));
		} catch (Exception e) {
			LOG.error("Error in searchGroups", e);
			return new ToolResponse(true, List.of(new TextContent("Error searching groups: " + e.getMessage())));
		}
	}

	/**
	 * Get a group from the OpenJDK census by its ID.
	 */
	@Tool(description = "Get information about a group in the OpenJDK census by its ID.")
	public ToolResponse getGroup(
			@ToolArg(description = "The group ID") String groupid) {
		try {
			LOG.info("MCP tool: Getting group with ID: " + groupid);
			CensusService censusService = helperService.getCensusService();
			Response response = censusService.getGroups(groupid);

			if (response.getStatus() != Response.Status.OK.getStatusCode()) {
				LOG.error("Error fetching group: " + response.getEntity().toString());
				return new ToolResponse(true, List.of(new TextContent("Error fetching group: " + response.getEntity().toString())));
			}

			return ToolResponse.success(new TextContent(response.getEntity().toString()));
		} catch (Exception e) {
			LOG.error("Error in getGroup", e);
			return new ToolResponse(true, List.of(new TextContent("Error fetching group: " + e.getMessage())));
		}
	}

	/**
	 * Get GitHub repositories for a user.
	 */
	@Tool(description = "Get GitHub repositories for a user.")
	public ToolResponse getGitHubRepos(
			@ToolArg(description = "The GitHub username") String user) {
		try {
			LOG.info("MCP tool: Fetching repositories for user: " + user);
			GitHubService gitHubService = helperService.getGitHubService();
			Response response = gitHubService.getRepositories(user);

			if (response.getStatus() != Response.Status.OK.getStatusCode()) {
				LOG.error("Error fetching repositories: " + response.getEntity().toString());
				return new ToolResponse(true, List.of(new TextContent("Error fetching repositories: " + response.getEntity().toString())));
			}

			return ToolResponse.success(new TextContent(response.getEntity().toString()));
		} catch (Exception e) {
			LOG.error("Error in getGitHubRepos", e);
			return new ToolResponse(true, List.of(new TextContent("Error fetching repositories: " + e.getMessage())));
		}
	}

	/**
	 * Get pull requests for a repository.
	 */
	@Tool(description = "Get pull requests for a GitHub repository.")
	public ToolResponse getGitHubPulls(
			@ToolArg(description = "The repository owner") String owner, @ToolArg(description = "The repository name") String repo) {
		try {
			LOG.info("MCP tool: Fetching pull requests for repo: " + owner + "/" + repo);
			GitHubService gitHubService = helperService.getGitHubService();
			Response response = gitHubService.getPullRequests(owner, repo, null, null, null, null, -1);

			if (response.getStatus() != Response.Status.OK.getStatusCode()) {
				LOG.error("Error fetching pull requests: " + response.getEntity().toString());
				return new ToolResponse(true, List.of(new TextContent("Error fetching pull requests: " + response.getEntity().toString())));
			}

			return ToolResponse.success(new TextContent(response.getEntity().toString()));
		} catch (Exception e) {
			LOG.error("Error in getGitHubPulls", e);
			return new ToolResponse(true, List.of(new TextContent("Error fetching pull requests: " + e.getMessage())));
		}
	}
}
