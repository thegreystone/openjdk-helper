/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.processes;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
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
	@APIResponse(responseCode = "200", description = "Curated process summaries with links to the governing documents", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.OBJECT, properties = {
			@SchemaProperty(name = "reviewedOn", type = SchemaType.STRING, description = "Date the summaries were last checked against their sources"),
			@SchemaProperty(name = "scope", type = SchemaType.STRING, description = "What the summaries cover and how to use them"),
			@SchemaProperty(name = "topics", type = SchemaType.ARRAY, implementation = String.class, description = "Topic IDs available for filtering"),
			@SchemaProperty(name = "results", type = SchemaType.ARRAY, implementation = Object.class, description = "Process summaries with id, title, appliesTo, summary, steps and links"),
			@SchemaProperty(name = "project", description = "Resolved census project with name, id and censusUrl, present when projectid is given"),
			@SchemaProperty(name = "censusMetadata", description = "Census snapshot metadata, present when projectid is given"),
			@SchemaProperty(name = "projectContext", type = SchemaType.STRING, description = "Guidance on which tools give exact project data, present when projectid is given")})))
	public Map<String, Object> getProjectProcesses(
		@QueryParam("projectid") @Parameter(description = "Optional census project ID, for example jmc or jdk-updates")
		String projectId,
		@QueryParam("topic") @Parameter(description = "Optional topic filter, for example committer, csr, jep, backports or lead")
		String topic) {
		return service.getProjectProcesses(projectId, topic);
	}
}
