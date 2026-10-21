/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.contribution;

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
import java.util.Map;

@Path("/contribution")
@Produces(MediaType.APPLICATION_JSON)
public class ContributionResource {
	@Inject
	ContributionService service;

	@GET
	@Path("/context")
	@APIResponse(responseCode = "200", description = "Project identity and review rules resolved from the .jcheck/conf of the repository", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.OBJECT, properties = {
			@SchemaProperty(name = "owner", type = SchemaType.STRING, description = "Repository owner"),
			@SchemaProperty(name = "repo", type = SchemaType.STRING, description = "Repository name"),
			@SchemaProperty(name = "ref", type = SchemaType.STRING, description = "Git ref the configuration was read from"),
			@SchemaProperty(name = "repositoryUrl", type = SchemaType.STRING, description = "GitHub repository URL"),
			@SchemaProperty(name = "source", description = "Fetch metadata for .jcheck/conf: path, url, fetchedAt, cached and status"),
			@SchemaProperty(name = "projectId", type = SchemaType.STRING, description = "Census project ID declared in .jcheck/conf"),
			@SchemaProperty(name = "jbsProject", type = SchemaType.STRING, description = "JBS project key declared in .jcheck/conf"),
			@SchemaProperty(name = "version", type = SchemaType.STRING, description = "Version declared in .jcheck/conf"),
			@SchemaProperty(name = "reviewRules", description = "The [checks \"reviewers\"] section of .jcheck/conf, for example minimum and role"),
			@SchemaProperty(name = "committerRules", description = "The [checks \"committer\"] section of .jcheck/conf"),
			@SchemaProperty(name = "checks", description = "The [checks] section of .jcheck/conf, for example error and warning lists"),
			@SchemaProperty(name = "ruleSemantics", type = SchemaType.STRING, description = "How to interpret the rule values"),
			@SchemaProperty(name = "censusUrl", type = SchemaType.STRING, description = "Census page of the resolved project"),
			@SchemaProperty(name = "censusMembers", description = "Project members with exact census roles, as returned by the members endpoint"),
			@SchemaProperty(name = "censusStatus", type = SchemaType.INTEGER, description = "HTTP status of the census member lookup"),
			@SchemaProperty(name = "memberSemantics", type = SchemaType.STRING, description = "How to interpret the member list")})))
	public Map<String, Object> context(@QueryParam("owner")
	String owner, @QueryParam("repo")
	String repo, @QueryParam("ref")
	String ref) {
		return service.getContributionContext(owner, repo, ref);
	}

	@GET
	@Path("/build-guidance")
	@APIResponse(responseCode = "200", description = "Verbatim build and test documentation excerpts from the repository", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.OBJECT, properties = {
			@SchemaProperty(name = "owner", type = SchemaType.STRING, description = "Repository owner"),
			@SchemaProperty(name = "repo", type = SchemaType.STRING, description = "Repository name"),
			@SchemaProperty(name = "ref", type = SchemaType.STRING, description = "Git ref the documents were read from"),
			@SchemaProperty(name = "repositoryUrl", type = SchemaType.STRING, description = "GitHub repository URL"),
			@SchemaProperty(name = "topic", type = SchemaType.STRING, description = "Requested topic filter, empty when not given"),
			@SchemaProperty(name = "test", type = SchemaType.STRING, description = "Requested test name or path, empty when not given"),
			@SchemaProperty(name = "documents", type = SchemaType.ARRAY, implementation = Object.class, description = "Repository documents with status, availability, url, path and matching excerpts"),
			@SchemaProperty(name = "problemLists", description = "Problem list matches for the requested test, present only when test is given"),
			@SchemaProperty(name = "guidanceSemantics", type = SchemaType.STRING, description = "How to interpret the excerpts")})))
	public Map<String, Object> buildGuidance(@QueryParam("owner")
	String owner, @QueryParam("repo")
	String repo, @QueryParam("ref")
	String ref, @QueryParam("topic")
	String topic, @QueryParam("test")
	String test) {
		return service.getBuildGuidance(owner, repo, ref, topic, test);
	}
}
