/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.jeps;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import se.hirt.openjdk.helper.common.ApiSupport;
import se.hirt.openjdk.helper.common.UpstreamHttp;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Reads the published JEP pages; missing metadata is omitted rather than inferred. */
@ApplicationScoped
public class JepService {

	private static final int SUMMARY_LIMIT = 4000;
	private static final Pattern TITLE = Pattern.compile("^JEP\\s+(\\d+|draft):\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	private static final Map<String, String> FIELDS = Map.ofEntries(
			Map.entry("Owner", "owner"), Map.entry("Type", "type"), Map.entry("Scope", "scope"),
			Map.entry("Status", "status"), Map.entry("Release", "release"), Map.entry("Component", "component"),
			Map.entry("Discussion", "discussion"), Map.entry("Effort", "effort"), Map.entry("Duration", "duration"),
			Map.entry("Created", "created"), Map.entry("Updated", "updated"));

	@Inject
	UpstreamHttp http;
	@ConfigProperty(name = "openjdk.jeps.base-url", defaultValue = "https://openjdk.org")
	String baseUrl = "https://openjdk.org";

	public JepService() {
	}

	JepService(UpstreamHttp http, String baseUrl) {
		this.http = http;
		this.baseUrl = baseUrl;
	}

	public Response getJep(int number) {
		if (number < 0 || number > 9_999_999) return invalid("JEP number must be between 0 and 9999999");
		URI source = source(number);
		var fetched = http.get(source, null);
		Document page = Jsoup.parse(fetched.body(), source.toString());
		Element heading = page.selectFirst("h1");
		var title = TITLE.matcher(heading == null ? "" : normalized(heading.text()));
		if (!title.matches() || (!title.group(1).equalsIgnoreCase("draft")
				&& !Integer.toString(number).equals(title.group(1))) || page.select("table.head tr").isEmpty()) {
			return malformed(source);
		}
		var jep = new LinkedHashMap<String, Object>();
		jep.put("number", number);
		jep.put("title", title.group(2));
		jep.put("url", source.toString());
		if (title.group(1).equalsIgnoreCase("draft")) jep.put("draft", true);
		var links = new ArrayList<Map<String, String>>();
		for (Element row : page.select("table.head tr")) {
			var cells = row.select("td");
			if (cells.size() < 2) continue;
			String label = normalized(cells.get(0).text());
			String value = normalized(cells.get(1).text());
			String field = FIELDS.get(label);
			if (field != null && !value.isBlank()) jep.put(field, bounded(value, 2000));
			for (Element link : cells.get(1).select("a[href]")) {
				String url = link.absUrl("href");
				if (links.size() < 50 && (url.startsWith("https://") || url.startsWith("http://"))) {
					links.add(Map.of("label", label, "title", bounded(normalized(link.text()), 300), "url", url));
				}
			}
		}
		if (!jep.containsKey("status") || !jep.containsKey("type")) return malformed(source);
		Element summaryHeading = page.getElementById("Summary");
		if (summaryHeading != null) {
			var summary = new StringBuilder();
			for (Element element = summaryHeading.nextElementSibling(); element != null
					&& !element.tagName().matches("h[12]"); element = element.nextElementSibling()) {
				String value = normalized(element.text());
				if (!value.isBlank()) {
					if (!summary.isEmpty()) summary.append('\n');
					summary.append(value);
				}
				if (summary.length() > SUMMARY_LIMIT) break;
			}
			if (!summary.isEmpty()) {
				jep.put("summary", bounded(summary.toString(), SUMMARY_LIMIT));
				jep.put("summaryTruncated", summary.length() > SUMMARY_LIMIT);
			}
		}
		jep.put("links", links);
		return Response.ok(Map.of("status", "available", "source", source.toString(),
				"fetchedAt", fetched.fetchedAt().toString(), "cached", fetched.cached(), "jep", jep)).build();
	}

	public Response searchJeps(String query, String status, String release, Integer limit, Integer offset) {
		int pageSize;
		int start;
		String text;
		String state;
		String targetRelease;
		try {
			pageSize = ApiSupport.limit(limit);
			start = ApiSupport.offset(offset);
			text = filter(query, "query", 300);
			state = filter(status, "status", 80);
			targetRelease = filter(release, "release", 40);
		} catch (WebApplicationException e) {
			return e.getResponse();
		} catch (IllegalArgumentException e) {
			return invalid(e.getMessage());
		}
		URI source = source(0);
		var fetched = http.get(source, null);
		Document index = Jsoup.parse(fetched.body(), source.toString());
		var entries = new LinkedHashMap<Integer, Map<String, Object>>();
		for (Element row : index.select("table.jeps tr")) {
			Element numberCell = row.selectFirst("td.jep");
			Element link = row.selectFirst("td:last-child a[href]");
			if (numberCell == null || link == null || !numberCell.text().matches("[0-9]{1,7}")) continue;
			int number = Integer.parseInt(numberCell.text());
			String title = normalized(link.text());
			String entryStatus = attribute(row, "Status");
			if (title.isBlank() || entryStatus.isBlank()) continue;
			var jep = new LinkedHashMap<String, Object>();
			jep.put("number", number);
			jep.put("title", title);
			jep.put("status", entryStatus);
			jep.put("url", source(number).toString());
			String type = attribute(row, "Type");
			if (!type.isBlank()) jep.put("type", type);
			String entryRelease = attribute(row, "Release");
			if (!entryRelease.isBlank()) jep.put("release", entryRelease);
			entries.putIfAbsent(number, jep);
		}
		if (entries.isEmpty()) return malformed(source);
		List<Map<String, Object>> matches = entries.values().stream().filter(jep ->
				(text.isEmpty() || jep.get("title").toString().toLowerCase(Locale.ROOT).contains(text)
						|| jep.get("number").toString().equals(text))
				&& (state.isEmpty() || matchesStatus(jep.get("status").toString(), state))
				&& (targetRelease.isEmpty() || jep.getOrDefault("release", "").toString().equalsIgnoreCase(targetRelease)))
				.toList();
		var results = matches.subList(Math.min(start, matches.size()), Math.min(start + pageSize, matches.size()));
		var result = new LinkedHashMap<String, Object>();
		result.put("status", "available");
		result.put("source", source.toString());
		result.put("fetchedAt", fetched.fetchedAt().toString());
		result.put("cached", fetched.cached());
		result.put("results", results);
		result.put("offset", start);
		result.put("limit", pageSize);
		result.put("total", matches.size());
		result.put("hasMore", start + results.size() < matches.size());
		return Response.ok(result).build();
	}

	private URI source(int number) {
		return ApiSupport.uri(baseUrl, "/jeps/" + number, Map.of());
	}

	private static String attribute(Element row, String name) {
		Element value = row.selectFirst("[title^='" + name + ":']");
		return value == null ? "" : normalized(value.attr("title").substring(name.length() + 1));
	}

	private static boolean matchesStatus(String actual, String expected) {
		if (actual.equalsIgnoreCase(expected)) return true;
		for (String component : actual.split("/")) if (component.strip().equalsIgnoreCase(expected)) return true;
		return false;
	}

	private static String filter(String value, String name, int maxLength) {
		if (value == null) return "";
		if (value.length() > maxLength || value.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException(name + " must have at most " + maxLength + " characters and no control characters");
		}
		return normalized(value).toLowerCase(Locale.ROOT);
	}

	private static String normalized(String value) {
		return value.replaceAll("[\\p{Z}\\s]+", " ").strip();
	}

	private static String bounded(String value, int limit) {
		return value.substring(0, Math.min(value.length(), limit));
	}

	private static Response malformed(URI source) {
		return Response.status(502).entity(Map.of("status", "unavailable", "source", source.toString(),
				"message", "OpenJDK returned an unexpected JEP page. Its format may have changed.")).build();
	}

	private static Response invalid(String message) {
		return Response.status(400).entity(Map.of("status", "invalidArgument", "message", message)).build();
	}
}
