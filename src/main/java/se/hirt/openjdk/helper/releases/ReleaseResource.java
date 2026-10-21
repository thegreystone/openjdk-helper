/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.releases;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

@Path("/releases")
@Produces(MediaType.APPLICATION_JSON)
public class ReleaseResource {
	@Inject
	ReleaseService releases;

	@GET
	@Path("/jdk/{release}")
	@Operation(summary = "Get a JDK feature release's schedule, current phase and targeted JEPs")
	public Response getReleaseSchedule(
			@PathParam("release") @Parameter(description = "Feature release number, for example 26") int release) {
		return releases.getReleaseSchedule(release);
	}
}
