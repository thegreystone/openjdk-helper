/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.issues;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

@Path("/issues")
@Produces(MediaType.APPLICATION_JSON)
public class IssueResource {

	@Inject
	IssueService issues;

	@GET
	@Path("/{key}")
	public Response getIssue(@PathParam("key")
	String key) {
		return issues.getIssue(key);
	}

	@GET
	@Path("/{key}/fix-status")
	public Response getIssueFixStatus(
		@PathParam("key") @Parameter(description = "Main issue key, for example JDK-8394772", example = "JDK-8394772")
		String key) {
		return issues.getIssueFixStatus(key);
	}

	@GET
	@Path("/search")
	public Response searchIssues(@QueryParam("query") @Parameter(description = "Literal text phrase")
	String query, @QueryParam("project") @Parameter(description = "JDK, JMC, CODETOOLS or SKARA")
	String project, @QueryParam("status") @Parameter(description = "Exact status, for example Open")
	String status,
		@QueryParam("type") @Parameter(description = "Exact issue type, for example Bug, Enhancement, CSR, JEP or Backport")
		String type, @QueryParam("fixVersion") @Parameter(description = "Exact fix version, for example 26 or 17.0.22")
		String fixVersion, @QueryParam("component") @Parameter(description = "Exact component, for example hotspot")
		String component, @QueryParam("priority") @Parameter(description = "Exact priority, for example P2")
		String priority, @QueryParam("assignee") @Parameter(description = "Assignee username, or unassigned")
		String assignee,
		@QueryParam("resolution") @Parameter(description = "Exact resolution such as Fixed, or unresolved")
		String resolution,
		@QueryParam("updatedSince") @Parameter(description = "Updated on or after this date, YYYY-MM-DD")
		String updatedSince,
		@QueryParam("resolvedSince") @Parameter(description = "Resolved on or after this date, YYYY-MM-DD")
		String resolvedSince, @QueryParam("limit")
		Integer limit, @QueryParam("offset")
		Integer offset) {
		return issues.searchIssues(new IssueService.Filters(query, project, status, type, fixVersion, component,
				priority, assignee, resolution, updatedSince, resolvedSince), limit, offset);
	}
}
