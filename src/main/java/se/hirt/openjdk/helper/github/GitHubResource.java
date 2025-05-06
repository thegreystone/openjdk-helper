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

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

@Path("/github")
public class GitHubResource {
	@Inject
	GitHubService githubService;

	@GET
	@Path("/repos")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getRepositories(
			@QueryParam("user") @Parameter(description = "The user for which to list the repositories.", example = "thegreystone")
			String user) {
		return githubService.getRepositories(user);
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
	@Parameter(description = "The maximum number of results. Defaults to -1 which means as many as we can get. Tips: to get the oldest PR, set maxresults to 1, sort to created and set the sort order to asc. To get the newest, set the sort order to desc.", example = "100")
	@DefaultValue("-1") int maxResults) {
		return githubService.getPullRequests(owner, repo, user, state, sort, direction, maxResults);
	}
}
