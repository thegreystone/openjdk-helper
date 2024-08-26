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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import java.util.List;
import java.util.Map;

@Path("/github")
public class GitHubResource {
	private static final String GITHUB_API_BASE_USER_URL = "https://api.github.com/users/";
	private final Client client = ClientBuilder.newClient();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@GET
	@Path("/repos")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getRepositories(@QueryParam("user")
	@Parameter(description = "The user for which to list the repositories.", example = "thegreystone")
	String user) {
		if (user == null || user.isEmpty()) {
			return Response.status(Response.Status.BAD_REQUEST)
					.entity("User parameter is required")
					.build();
		}

		String apiUrl = GITHUB_API_BASE_USER_URL + user + "/repos";

		try {
			Response response = client.target(apiUrl).request(MediaType.APPLICATION_JSON).get();

			if (response.getStatus() != 200) {
				return Response.status(response.getStatus())
						.entity("Error fetching data from GitHub API")
						.build();
			}

			String jsonResponse = response.readEntity(String.class);
			List<Map<String, Object>> repos = objectMapper.readValue(jsonResponse, List.class);

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
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Error processing request: " + e.getMessage())
					.build();
		}
	}
}
