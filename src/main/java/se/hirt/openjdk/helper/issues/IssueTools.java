/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.issues;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.hirt.openjdk.helper.common.ToolResults;

@ApplicationScoped
public class IssueTools {

	@Inject
	IssueService issues;
	@Inject
	ToolResults results;

	@Tool(description = "Read a JBS issue, its affected/fix versions and linked issues such as CSR requests and backports. Access failures are reported explicitly.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getIssue(@ToolArg(description = "Full JBS key, for example JDK-8344154, JMC-8639, CODETOOLS-7900000 or SKARA-2000") String key) {
		return results.call(() -> issues.getIssue(key));
	}

	@Tool(description = "Report where a fix landed: the main JBS issue plus every linked Backport issue with fix version, status and resolution, summarized as fixedIn and pendingIn release lists. Pass the main issue key, not a backport key.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getIssueFixStatus(@ToolArg(description = "Main JBS issue key, for example JDK-8394772") String key) {
		return results.call(() -> issues.getIssueFixStatus(key));
	}

	@Tool(description = "Search JBS with literal text and exact filters (project, status, type, fixVersion, component, priority, assignee, resolution, updatedSince, resolvedSince) with bounded pagination. Values are literals, not raw JQL; the generated JQL is returned.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse searchIssues(
			@ToolArg(description = "Literal text phrase to search for; omit to use only the filters", required = false) String query,
			@ToolArg(description = "JDK, JMC, CODETOOLS or SKARA; omit to search all four", required = false) String project,
			@ToolArg(description = "Exact issue status, for example Open, In Progress or Resolved", required = false) String status,
			@ToolArg(description = "Exact issue type, for example Bug, Enhancement, CSR, JEP, Backport or Task", required = false) String type,
			@ToolArg(description = "Exact fix version, for example 26, 17.0.22 or 10", required = false) String fixVersion,
			@ToolArg(description = "Exact component, for example hotspot, core-libs or jmc", required = false) String component,
			@ToolArg(description = "Exact priority, for example P1 to P5", required = false) String priority,
			@ToolArg(description = "Assignee username, or unassigned", required = false) String assignee,
			@ToolArg(description = "Exact resolution such as Fixed or Won't Fix, or unresolved", required = false) String resolution,
			@ToolArg(description = "Only issues updated on or after this date, YYYY-MM-DD", required = false) String updatedSince,
			@ToolArg(description = "Only issues resolved on or after this date, YYYY-MM-DD", required = false) String resolvedSince,
			@ToolArg(description = "Page size from 1 to 100, default 30", required = false) Integer limit,
			@ToolArg(description = "Zero-based result offset, default 0", required = false) Integer offset) {
		return results.call(() -> issues.searchIssues(new IssueService.Filters(query, project, status, type, fixVersion, component,
				priority, assignee, resolution, updatedSince, resolvedSince), limit, offset));
	}
}
