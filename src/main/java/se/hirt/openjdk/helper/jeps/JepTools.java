/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.jeps;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.hirt.openjdk.helper.common.ToolResults;

@ApplicationScoped
public class JepTools {
	@Inject
	JepService jeps;
	@Inject
	ToolResults results;

	@Tool(description = "Read a published JEP's title, status, release, metadata, source links and bounded summary. Missing metadata is omitted.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getJep(@ToolArg(description = "JEP number, for example 517; 0 is the JEP index") int number) {
		return results.call(() -> jeps.getJep(number));
	}

	@Tool(description = "Search the official JEP index by title or number, lifecycle status and target release with bounded pagination.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse searchJeps(
			@ToolArg(description = "Case-insensitive title text or exact JEP number", required = false) String query,
			@ToolArg(description = "Lifecycle status, for example Candidate, Targeted or Delivered", required = false) String status,
			@ToolArg(description = "Target release from the index, for example 26", required = false) String release,
			@ToolArg(description = "Page size from 1 to 100, default 30", required = false) Integer limit,
			@ToolArg(description = "Zero-based result offset, default 0", required = false) Integer offset) {
		return results.call(() -> jeps.searchJeps(query, status, release, limit, offset));
	}
}
