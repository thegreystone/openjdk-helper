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
package se.hirt.openjdk.helper.census;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

@Path("/census")
public class CensusResource {
	@Inject
	CensusService censusService;

	@GET
	@Path("/status")
	@Produces(MediaType.APPLICATION_JSON)
	@APIResponse(responseCode = "200", description = "Metadata about the loaded census snapshot", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.OBJECT, properties = {
			@SchemaProperty(name = "sourceUrl", type = SchemaType.STRING, description = "Census document the snapshot was read from"),
			@SchemaProperty(name = "format", type = SchemaType.STRING, description = "Parsed document format, xml or html"),
			@SchemaProperty(name = "sourceTime", type = SchemaType.STRING, description = "Timestamp published in the census document"),
			@SchemaProperty(name = "lastSuccessfulRefresh", type = SchemaType.STRING, description = "When the snapshot was last refreshed successfully"),
			@SchemaProperty(name = "lastRefreshAttempt", type = SchemaType.STRING, description = "When a refresh was last attempted"),
			@SchemaProperty(name = "available", type = SchemaType.BOOLEAN, description = "Whether a census snapshot is loaded"),
			@SchemaProperty(name = "stale", type = SchemaType.BOOLEAN, description = "Whether the snapshot is older than the refresh interval"),
			@SchemaProperty(name = "lastRefreshFailed", type = SchemaType.BOOLEAN, description = "Whether the most recent refresh attempt failed")})))
	public java.util.Map<String, Object> getStatus() {
		return censusService.getCensusStatus();
	}

	@GET
	@Path("/projects/{projectid}/members")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getProjectMembers(@PathParam("projectid")
	String projectId, @QueryParam("roles")
	String roles, @QueryParam("query")
	String query, @QueryParam("limit")
	Integer limit, @QueryParam("offset")
	Integer offset) {
		return censusService.getProjectMembers(projectId, roles, query, limit, offset);
	}

	@GET
	@Path("/people")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPeople(
		@QueryParam("userid") @Parameter(description = "Lists the person(s) with the specified userid(s). Multiple IDs can be provided as a comma-separated list (e.g., 'hirt,duke,iris'). If no userid is provided, returns all people in OpenJDK.", example = "hirt,duke")
		String userId) {
		return censusService.getPeople(userId);
	}

	@GET
	@Path("/people/search")
	@Produces(MediaType.APPLICATION_JSON)
	public Response searchPeople(
		@QueryParam("query") @Parameter(description = "Search query for people using a regular expression. Searches through both userid and full names.", example = ".*Hirt")
		String query) {
		return censusService.searchPeople(query);
	}

	@GET
	@Path("/projects")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getProjects(
		@QueryParam("projectid") @Parameter(description = "Lists the project for the projectid, or all projects if not provided.", example = "jmc")
		String projectId) {
		return censusService.getProjects(projectId);
	}

	@GET
	@Path("/projects/search")
	@Produces(MediaType.APPLICATION_JSON)
	public Response searchProjects(
		@QueryParam("query") @Parameter(description = "Search query for projects using a regular expression. Searches both projectid and project names.", example = ".*Mission.*")
		String query) {
		return censusService.searchProjects(query);
	}

	@GET
	@Path("/groups")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getGroups(
		@QueryParam("groupid") @Parameter(description = "Lists the group for the groupid, or all if not provided.", example = "vulnerability")
		String groupId) {
		return censusService.getGroups(groupId);
	}

	@GET
	@Path("/groups/search")
	@Produces(MediaType.APPLICATION_JSON)
	public Response searchGroups(
		@QueryParam("query") @Parameter(description = "Search query for groups using a regular expression. Searches both groupid and group names.", example = ".*uln.*")
		String query) {
		return censusService.searchGroups(query);
	}
}
