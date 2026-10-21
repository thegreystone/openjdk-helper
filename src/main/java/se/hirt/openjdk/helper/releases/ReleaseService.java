/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.releases;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import se.hirt.openjdk.helper.common.ApiSupport;
import se.hirt.openjdk.helper.common.UpstreamHttp;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads the published JDK release pages; the current phase is derived only by comparing listed
 * dates with today's date.
 */
@ApplicationScoped
public class ReleaseService {
	private static final Pattern TITLE = Pattern.compile("^JDK\\s+(\\d+)$");
	private static final DateTimeFormatter MILESTONE_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	@Inject
	UpstreamHttp http;
	@ConfigProperty(name = "openjdk.site.base-url", defaultValue = "https://openjdk.org")
	String baseUrl = "https://openjdk.org";
	Clock clock = Clock.systemUTC();

	public ReleaseService() {
	}

	ReleaseService(UpstreamHttp http, String baseUrl, Clock clock) {
		this.http = http;
		this.baseUrl = baseUrl;
		this.clock = clock;
	}

	public Response getReleaseSchedule(int release) {
		if (release < 9 || release > 99)
			return invalid("release must be a feature release number between 9 and 99");
		URI source = ApiSupport.uri(baseUrl, "/projects/jdk/" + release + "/", Map.of());
		var fetched = http.get(source, null);
		Document page = Jsoup.parse(fetched.body(), source.toString());
		Element heading = page.selectFirst("h1");
		var title = TITLE.matcher(heading == null ? "" : normalized(heading.text()));
		if (!title.matches() || Integer.parseInt(title.group(1)) != release)
			return malformed(source);
		LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
		List<Map<String, Object>> milestones = new ArrayList<>();
		String currentPhase = "Before Rampdown Phase One";
		Map<String, Object> next = null;
		String generalAvailability = null;
		for (Element row : page.select("tr.milestone")) {
			var cells = row.select("td");
			if (cells.size() < 2)
				continue;
			LocalDate date;
			try {
				date = LocalDate.parse(normalized(cells.get(0).text()), MILESTONE_DATE);
			} catch (DateTimeParseException e) {
				continue;
			}
			Element comment = row.selectFirst("td.comment");
			String name = normalized((comment == null ? cells.get(cells.size() - 1) : comment).text());
			if (name.isEmpty())
				continue;
			Map<String, Object> milestone = new LinkedHashMap<>();
			milestone.put("date", date.toString());
			milestone.put("name", name);
			Element link = row.selectFirst("a[href]");
			if (link != null)
				milestone.put("url", link.absUrl("href"));
			boolean reached = !date.isAfter(today);
			milestone.put("reached", reached);
			if (reached)
				currentPhase = name;
			else if (next == null)
				next = milestone;
			if (name.toLowerCase(java.util.Locale.ROOT).contains("general availability"))
				generalAvailability = date.toString();
			milestones.add(milestone);
		}
		if (milestones.isEmpty())
			return malformed(source);
		List<Map<String, Object>> features = new ArrayList<>();
		for (Element row : page.select("table.jeps tr")) {
			var cells = row.select("td");
			Element link = row.selectFirst("a[href]");
			if (cells.size() < 2 || link == null)
				continue;
			String number = normalized(cells.get(0).text()).replaceAll("[^0-9]", "");
			if (number.isEmpty() || number.length() > 7)
				continue;
			features.add(Map.of("number", Integer.parseInt(number), "title", bounded(normalized(link.text()), 300),
					"url", link.absUrl("href")));
		}
		var result = new LinkedHashMap<String, Object>();
		result.put("status", "available");
		result.put("source", source.toString());
		result.put("fetchedAt", fetched.fetchedAt().toString());
		result.put("cached", fetched.cached());
		result.put("release", release);
		result.put("evaluatedOn", today.toString());
		result.put("currentPhase", currentPhase);
		result.put("nextMilestone", next);
		result.put("generalAvailability", generalAvailability);
		result.put("shipped", generalAvailability != null && !LocalDate.parse(generalAvailability).isAfter(today));
		result.put("milestones", milestones);
		result.put("features", features);
		Element lastUpdate = page.selectFirst("div.last-update");
		if (lastUpdate != null)
			result.put("pageLastUpdate", normalized(lastUpdate.text()).replaceFirst("(?i)^last update:\\s*", ""));
		result.put("semantics",
				"Milestone dates and targeted JEPs come from the release page at fetchedAt. currentPhase is the latest milestone whose date is not after evaluatedOn (UTC); it does not account for schedule changes announced elsewhere. Rampdown rules and fix-request procedures are defined in JEP 3.");
		return Response.ok(result).build();
	}

	private static String normalized(String value) {
		return value.replaceAll("[\\p{Z}\\s]+", " ").strip();
	}

	private static String bounded(String value, int limit) {
		return value.substring(0, Math.min(value.length(), limit));
	}

	private static Response malformed(URI source) {
		return Response.status(502).entity(Map.of("status", "unavailable", "source", source.toString(), "message",
				"OpenJDK returned an unexpected release page. Its format may have changed or the release may not exist."))
				.build();
	}

	private static Response invalid(String message) {
		return Response.status(400).entity(Map.of("status", "invalidArgument", "message", message)).build();
	}
}
