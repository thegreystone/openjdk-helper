/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.jeps;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/jeps")
@Produces(MediaType.APPLICATION_JSON)
public class JepResource {
	@Inject
	JepService jeps;

	@GET
	@Path("/{number}")
	public Response getJep(@PathParam("number") int number) {
		return jeps.getJep(number);
	}

	@GET
	@Path("/search")
	public Response searchJeps(@QueryParam("query") String query, @QueryParam("status") String status,
			@QueryParam("release") String release, @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
		return jeps.searchJeps(query, status, release, limit, offset);
	}
}
