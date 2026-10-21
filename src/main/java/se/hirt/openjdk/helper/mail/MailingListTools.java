/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.mail;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.hirt.openjdk.helper.common.ToolResults;

@ApplicationScoped
public class MailingListTools {
	@Inject
	MailingListService mail;
	@Inject
	ToolResults results;

	@Tool(description = "List or search one month of an OpenJDK mailing list archive (mail.openjdk.org): subjects, authors, thread depth and message links, with vote (CFV/Result), review-request and integration subjects classified. Message bodies are not read.",
			annotations = @Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
	public ToolResponse searchMailingList(
			@ToolArg(description = "Mailing list name, for example jmc-dev, jdk-dev or hotspot-jfr-dev") String list,
			@ToolArg(description = "Archive month as YYYY-MM or YYYY-MonthName; defaults to the newest archived month", required = false) String month,
			@ToolArg(description = "Case-insensitive literal text matched against subjects and authors, for example CFV or 8341234", required = false) String query,
			@ToolArg(description = "Page size, 1 to 100; default 30", required = false) Integer limit,
			@ToolArg(description = "Zero-based result offset; default 0", required = false) Integer offset) {
		return results.call(() -> mail.searchThreads(list, month, query, limit, offset));
	}
}
