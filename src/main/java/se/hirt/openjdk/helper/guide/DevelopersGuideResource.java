/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.guide;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

@Path("/guide")
@Produces(MediaType.APPLICATION_JSON)
public class DevelopersGuideResource {
	@Inject
	DevelopersGuideService guide;

	@GET
	@Path("/search")
	@Operation(summary = "Search the OpenJDK Developers' Guide for verbatim section excerpts")
	public Response searchDevelopersGuide(
		@QueryParam("query") @Parameter(description = "Case-insensitive literal text; omit for the table of contents", example = "backport")
		String query,
		@QueryParam("limit") @Parameter(description = "Maximum sections to return, 1 to 20; defaults to 5")
		Integer limit) {
		return guide.search(query, limit);
	}
}
