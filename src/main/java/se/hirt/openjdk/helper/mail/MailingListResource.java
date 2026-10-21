/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.mail;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

@Path("/mail")
@Produces(MediaType.APPLICATION_JSON)
public class MailingListResource {
	@Inject
	MailingListService mail;

	@GET
	@Path("/threads")
	@Operation(summary = "List or search one month of an OpenJDK mailing list archive")
	public Response searchThreads(
			@QueryParam("list") @Parameter(required = true, description = "Mailing list name", example = "jmc-dev") String list,
			@QueryParam("month") @Parameter(description = "Archive month as YYYY-MM or YYYY-MonthName; defaults to the newest archived month", example = "2026-02") String month,
			@QueryParam("query") @Parameter(description = "Case-insensitive literal text matched against subjects and authors") String query,
			@QueryParam("limit") @Parameter(description = "Page size, 1 to 100; defaults to 30") Integer limit,
			@QueryParam("offset") @Parameter(description = "Zero-based result offset; defaults to 0") Integer offset) {
		return mail.searchThreads(list, month, query, limit, offset);
	}
}
