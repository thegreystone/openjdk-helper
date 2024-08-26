/*
 * Copyright (C) 2024 Marcus Hirt
 *                    www.hirt.se
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
 *
 * Copyright (C) Marcus Hirt, 2024
 */
package se.hirt.openjdk.helper.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import java.util.List;
import java.util.Map;

@Path("/github")
public class GitHubResource {
	private static final String GITHUB_API_BASE_URL = "https://api.github.com/";
	private static final String GITHUB_API_BASE_USER_URL = GITHUB_API_BASE_URL + "users/";
	private final Client client = ClientBuilder.newClient();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@GET
	@Path("/repos")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getRepositories(
			@QueryParam("user") @Parameter(description = "The user for which to list the repositories.", example = "thegreystone")
			String user) {
		if (user == null || user.isEmpty()) {
			return Response.status(Response.Status.BAD_REQUEST).entity("User parameter is required").build();
		}

		String apiUrl = GITHUB_API_BASE_USER_URL + user + "/repos";

		try {
			Response response = client.target(apiUrl).request(MediaType.APPLICATION_JSON).get();

			if (response.getStatus() != 200) {
				return Response.status(response.getStatus()).entity("Error fetching data from GitHub API").build();
			}

			String jsonResponse = response.readEntity(String.class);
			@SuppressWarnings("unchecked") List<Map<String, Object>> repos = objectMapper.readValue(jsonResponse, List.class);

			ArrayNode condensedRepos = objectMapper.createArrayNode();

			for (Map<String, Object> repo : repos) {
				ObjectNode condensedRepo = objectMapper.createObjectNode();
				condensedRepo.put("name", (String) repo.get("name"));
				condensedRepo.put("description", (String) repo.get("description"));
				condensedRepo.put("stars", ((Integer) repo.get("stargazers_count")).intValue());
				condensedRepo.put("created_at", (String) repo.get("created_at"));
				condensedRepo.put("pushed_at", (String) repo.get("pushed_at"));
				condensedRepo.put("updated_at", (String) repo.get("updated_at"));
				condensedRepos.add(condensedRepo);
			}

			return Response.ok(condensedRepos.toString()).build();

		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error processing request: " + e.getMessage()).build();
		}
	}

	@GET
	@Path("/pulls")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPullRequests(
			@QueryParam("owner") @Parameter(description = "The owner of the repository", example = "openjdk", required = true) String owner,
			@QueryParam("repo") @Parameter(description = "The name of the repository", example = "jmc", required = true) String repo,
			@QueryParam("user") @Parameter(description = "The user opening the PR (optional)", example = "thegreystone") String user,
			@QueryParam("state") @Parameter(description = "The state of the PRs to list, [open|closed|all]", example = "open")
			@DefaultValue("open") String state,
			@QueryParam("sort") @Parameter(description = "In what order to get the results, [created|updated|popularity|long-running]", example = "created")
			@DefaultValue("created") String sort,
			@QueryParam("direction") @Parameter(description = "The sort order, [asc|desc]", example = "desc")
			@DefaultValue("desc") String direction,
			@QueryParam("maxresults") @Parameter(description = "The maximum number of results. Defaults to -1 which means as many as we can get. Tips: to get the oldest PR, set maxresults to 1, sort to created and set the sort order to asc. To get the newest, set the sort order to desc.", example = "100") @DefaultValue("-1") int maxResults) {
		if (owner == null || owner.isEmpty() || repo == null || repo.isEmpty()) {
			return Response.status(Response.Status.BAD_REQUEST).entity("Both owner and repo parameters are required").build();
		}

		if (state == null || state.isEmpty()) {
			state = "open";
		} else {
			state = state.trim();
			if (!(state.equals("open") || state.equals("closed") || state.equals("all"))) {
				return Response.status(Response.Status.BAD_REQUEST).entity("Invalid state: " + state).build();
			}
		}
		if (sort == null || sort.isEmpty()) {
			sort = "created";
		} else {
			sort = sort.trim();
			if (!(sort.equals("created") || sort.equals("updated") || sort.equals("popularity") || sort.equals("long-running"))) {
				return Response.status(Response.Status.BAD_REQUEST).entity("Invalid sort: " + sort).build();
			}
		}
		if (maxResults == -1) maxResults = Integer.MAX_VALUE;

		try {
			ArrayNode condensedPulls = objectMapper.createArrayNode();
			int page = 1;
			int matchingEntries = 0;
			List<Map<String, Object>> pulls;

			do {
				String apiUrl = String.format("%srepos/%s/%s/pulls?state=%s&per_page=100&page=%d&sort=%s&direction=%s", GITHUB_API_BASE_URL, owner, repo, state,
						page, sort, direction);
				Response response = client.target(apiUrl).request(MediaType.APPLICATION_JSON).get();

				if (response.getStatus() != 200) {
					return Response.status(response.getStatus()).entity("Error fetching data from GitHub API").build();
				}

				String jsonResponse = response.readEntity(String.class);
				//noinspection unchecked
				pulls = objectMapper.readValue(jsonResponse, List.class);

				for (Map<String, Object> pull : pulls) {
					ObjectNode condensedPull = objectMapper.createObjectNode();

					condensedPull.put("state", (String) pull.get("state"));
					condensedPull.put("number", (Integer) pull.get("number"));
					condensedPull.put("title", (String) pull.get("title"));
					condensedPull.put("created_at", (String) pull.get("created_at"));
					condensedPull.put("updated_at", (String) pull.get("updated_at"));

					String body = (String) pull.get("body");
					if (body != null) {
						int markerIndex = body.indexOf("<!--");
						if (markerIndex != -1) {
							body = body.substring(0, markerIndex).trim();
						}
					}
					condensedPull.put("body", body);

					@SuppressWarnings("unchecked") Map<String, Object> userMap = (Map<String, Object>) pull.get("user");
					if (userMap != null) {
						condensedPull.put("user", (String) userMap.get("login"));
					}
					if (user != null && !user.isEmpty()) {
						if (userMap != null && !user.trim().equals(userMap.get("login"))) {
							continue;
						}
					}
					condensedPulls.add(condensedPull);
					matchingEntries++;
					if (matchingEntries >= maxResults) {
						break;
					}
				}
				if (matchingEntries >= maxResults) {
					break;
				}
				page++;
			} while (pulls.size() == 100);

			return Response.ok(condensedPulls.toString()).build();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error processing request: " + e.getMessage()).build();
		}
	}
}
