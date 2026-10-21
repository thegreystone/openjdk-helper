/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.guide;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.hirt.openjdk.helper.common.ToolResults;

@ApplicationScoped
public class DevelopersGuideTools {
	@Inject
	DevelopersGuideService guide;
	@Inject
	ToolResults results;

	@Tool(description = "Search the OpenJDK Developers' Guide by literal text and get verbatim, bounded section excerpts with links; omit the query for the table of contents. Complements getProjectProcesses for topics beyond the curated catalog.", annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse searchDevelopersGuide(
		@ToolArg(description = "Case-insensitive literal text, for example backport, sponsor or CSR; omit for the table of contents", required = false)
		String query, @ToolArg(description = "Maximum sections to return, 1 to 20; default 5", required = false)
		Integer limit) {
		return results.call(() -> guide.search(query, limit));
	}
}
