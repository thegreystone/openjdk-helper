/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.processes;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.hirt.openjdk.helper.common.ToolResults;

@ApplicationScoped
public class ProjectProcessTools {
	@Inject
	ProjectProcessService service;
	@Inject
	ToolResults results;

	@Tool(description = "Summarize project-lead duties, Author appointments, Committer/Reviewer elections, voting, succession, reviews, CSR, JEPs and backports. Includes steps, applicability, review date and governing links.", annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getProjectProcesses(
		@ToolArg(description = "Optional census project ID, for example jmc or jdk-updates", required = false)
		String projectid,
		@ToolArg(description = "Optional topic ID or title filter; omit to list all processes", required = false)
		String topic) {
		return results.call(() -> service.getProjectProcesses(projectid, topic));
	}
}
