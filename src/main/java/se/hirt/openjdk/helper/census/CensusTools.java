/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.census;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.hirt.openjdk.helper.common.ToolResults;

@ApplicationScoped
public class CensusTools {
	@Inject
	CensusService census;
	@Inject
	ToolResults tools;

	@Tool(description = "List a bounded page of project members with exact census roles, names and source links. Role membership does not establish subject expertise or review availability.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getProjectMembers(@ToolArg(description = "OpenJDK census project ID, which can differ from the repository name") String projectId,
			@ToolArg(description = "Comma-separated exact roles: lead, reviewer, committer, author, member. Case-insensitive; omitted means all roles.", required = false) String roles,
			@ToolArg(description = "Optional case-insensitive literal name or OpenJDK ID filter", required = false) String query,
			@ToolArg(description = "Page size, 1 to 100; default 30", required = false) Integer limit,
			@ToolArg(description = "Zero-based offset in the members sorted by OpenJDK ID; default 0", required = false) Integer offset) {
		return tools.call(() -> census.getProjectMembers(projectId, roles, query, limit, offset));
	}

	@Tool(description = "Get the census snapshot source, publication time, last successful refresh, availability and stale status.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public ToolResponse getCensusStatus() {
		return tools.call(census::getCensusStatus);
	}
}
