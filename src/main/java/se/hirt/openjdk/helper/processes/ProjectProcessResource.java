/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.processes;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import java.util.Map;

@Path("/processes")
@Produces(MediaType.APPLICATION_JSON)
public class ProjectProcessResource {
	@Inject
	ProjectProcessService service;

	@GET
	@Operation(summary = "Summarize OpenJDK project processes with authoritative links")
	public Map<String, Object> getProjectProcesses(
		@QueryParam("projectid") @Parameter(description = "Optional census project ID, for example jmc or jdk-updates")
		String projectId,
		@QueryParam("topic") @Parameter(description = "Optional topic filter, for example committer, csr, jep, backports or lead")
		String topic) {
		return service.getProjectProcesses(projectId, topic);
	}
}
