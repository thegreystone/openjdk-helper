/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.contribution;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

@Path("/contribution")
@Produces(MediaType.APPLICATION_JSON)
public class ContributionResource {
	@Inject
	ContributionService service;

	@GET
	@Path("/context")
	public Map<String, Object> context(@QueryParam("owner")
	String owner, @QueryParam("repo")
	String repo, @QueryParam("ref")
	String ref) {
		return service.getContributionContext(owner, repo, ref);
	}

	@GET
	@Path("/build-guidance")
	public Map<String, Object> buildGuidance(@QueryParam("owner")
	String owner, @QueryParam("repo")
	String repo, @QueryParam("ref")
	String ref, @QueryParam("topic")
	String topic, @QueryParam("test")
	String test) {
		return service.getBuildGuidance(owner, repo, ref, topic, test);
	}
}
