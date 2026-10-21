/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.contribution;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.hirt.openjdk.helper.common.ToolResults;

@ApplicationScoped
public class ContributionTools {
	@Inject
	ContributionService service;
	@Inject
	ToolResults tools;

	@Tool(description = "Read the requested repository ref's .jcheck/conf to resolve its census project, JBS project and declared review rules, with exact census role candidates and source evidence.", annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getContributionContext(@ToolArg(description = "Repository owner, for example openjdk")
	String owner, @ToolArg(description = "Repository name, for example jdk17u or jfx")
	String repo,
		@ToolArg(description = "Branch, tag or commit SHA. Defaults to HEAD (the repository's default branch).", required = false)
		String ref) {
		return tools.call(() -> service.getContributionContext(owner, repo, ref));
	}

	@Tool(description = "Get bounded, verbatim build/test documentation excerpts for a repository ref, and optionally literal test matches in its ProblemList files. Returns provenance and missing-file statuses; does not invent or run commands.", annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getBuildGuidance(@ToolArg(description = "Repository owner, for example openjdk")
	String owner, @ToolArg(description = "Repository name, for example jdk")
	String repo, @ToolArg(description = "Branch, tag or commit SHA. Defaults to HEAD.", required = false)
	String ref,
		@ToolArg(description = "Literal documentation topic, for example boot-jdk, configure or jtreg. Omit for the introductory sections.", required = false)
		String topic,
		@ToolArg(description = "Optional literal test path or filename to inspect in ProblemList.txt files; results are not an exhaustive test exclusion assessment.", required = false)
		String test) {
		return tools.call(() -> service.getBuildGuidance(owner, repo, ref, topic, test));
	}
}
