/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.releases;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.hirt.openjdk.helper.common.ToolResults;

@ApplicationScoped
public class ReleaseTools {
	@Inject
	ReleaseService releases;
	@Inject
	ToolResults results;

	@Tool(description = "Get a JDK feature release's milestone schedule (Rampdown Phase One/Two, release candidates, General Availability), the phase reached today, the next milestone and the targeted JEPs, from the official release page.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse getReleaseSchedule(@ToolArg(description = "Feature release number, for example 26") int release) {
		return results.call(() -> releases.getReleaseSchedule(release));
	}
}
