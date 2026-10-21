/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.guide;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bounded verbatim excerpts from the OpenJDK Developers' Guide, located by heading or literal text.
 */
@ApplicationScoped
public class DevelopersGuideService {
	private static final int EXCERPT_LIMIT = 1500;
	private static final int SECTION_LIMIT = 40000;
	private static final int TOC_LIMIT = 150;

	@Inject
	UpstreamHttp http;
	@ConfigProperty(name = "openjdk.site.base-url", defaultValue = "https://openjdk.org")
	String baseUrl = "https://openjdk.org";

	public DevelopersGuideService() {
	}

	DevelopersGuideService(UpstreamHttp http, String baseUrl) {
		this.http = http;
		this.baseUrl = baseUrl;
	}

	public Response search(String query, Integer limit) {
		String text = query == null ? "" : query.strip();
		if (text.length() > 200 || text.chars().anyMatch(Character::isISOControl)) {
			return invalid("query must have at most 200 characters and no control characters");
		}
		int pageSize = limit == null ? 5 : limit;
		if (pageSize < 1 || pageSize > 20)
			return invalid("limit must be between 1 and 20");
		String needle = normalized(text).toLowerCase(Locale.ROOT);
		URI source = ApiSupport.uri(baseUrl, "/guide/", Map.of());
		var fetched = http.get(source, null);
		Document page = Jsoup.parse(fetched.body(), source.toString());
		List<Section> sections = sections(page, source);
		if (sections.isEmpty())
			return malformed(source);
		var result = new LinkedHashMap<String, Object>();
		result.put("status", "available");
		result.put("source", source.toString());
		result.put("fetchedAt", fetched.fetchedAt().toString());
		result.put("cached", fetched.cached());
		result.put("query", text);
		if (needle.isEmpty()) {
			List<Map<String, Object>> contents = new ArrayList<>();
			for (Section section : sections) {
				if (section.level() > 2 || contents.size() >= TOC_LIMIT)
					continue;
				contents.add(Map.of("id", section.id(), "title", section.title(), "level", section.level(), "url",
						section.url()));
			}
			result.put("sections", contents);
			result.put("total", sections.size());
			result.put("semantics",
					"Table of contents of the Developers' Guide down to second-level headings. Pass a query to receive verbatim excerpts.");
			return Response.ok(result).build();
		}
		List<Map<String, Object>> titleMatches = new ArrayList<>();
		List<Map<String, Object>> bodyMatches = new ArrayList<>();
		for (Section section : sections) {
			boolean inTitle = section.title().toLowerCase(Locale.ROOT).contains(needle);
			int position = section.text().toLowerCase(Locale.ROOT).indexOf(needle);
			if (!inTitle && position < 0)
				continue;
			(inTitle ? titleMatches : bodyMatches)
					.add(excerpt(section, inTitle ? 0 : position, inTitle ? "title" : "text"));
		}
		List<Map<String, Object>> matches = new ArrayList<>(titleMatches);
		matches.addAll(bodyMatches);
		result.put("results", matches.subList(0, Math.min(matches.size(), pageSize)));
		result.put("total", matches.size());
		result.put("hasMore", matches.size() > pageSize);
		result.put("semantics",
				"Excerpts are verbatim text of the matching section as published at fetchedAt, bounded to "
						+ EXCERPT_LIMIT
						+ " characters around the first match; read the linked section for the complete text. The guide describes common practice; project-specific rules and the bylaws take precedence.");
		return Response.ok(result).build();
	}

	private static Map<String, Object> excerpt(Section section, int position, String matchedOn) {
		String text = section.text();
		int start = Math.max(0, Math.min(position - EXCERPT_LIMIT / 4, Math.max(0, text.length() - EXCERPT_LIMIT)));
		int end = Math.min(text.length(), start + EXCERPT_LIMIT);
		var result = new LinkedHashMap<String, Object>();
		result.put("id", section.id());
		result.put("title", section.title());
		result.put("level", section.level());
		result.put("breadcrumb", section.breadcrumb());
		result.put("url", section.url());
		result.put("matchedOn", matchedOn);
		result.put("excerpt", (start > 0 ? "…" : "") + text.substring(start, end) + (end < text.length() ? "…" : ""));
		result.put("excerptStart", start);
		result.put("textLength", text.length());
		result.put("truncated", start > 0 || end < text.length());
		return result;
	}

	private static List<Section> sections(Document page, URI source) {
		List<Section> sections = new ArrayList<>();
		String[] parents = new String[4];
		for (Element heading : page.select("h1[id], h2[id], h3[id]")) {
			int level = heading.tagName().charAt(1) - '0';
			String title = normalized(heading.text());
			if (title.isEmpty())
				continue;
			parents[level] = title;
			List<String> breadcrumb = new ArrayList<>();
			for (int i = 1; i < level; i++)
				if (parents[i] != null)
					breadcrumb.add(parents[i]);
			var text = new StringBuilder();
			for (Element element = heading.nextElementSibling(); element != null
					&& !element.tagName().matches("h[1-3]"); element = element.nextElementSibling()) {
				String value = normalized(element.text());
				if (value.isEmpty())
					continue;
				if (!text.isEmpty())
					text.append('\n');
				text.append(value);
				if (text.length() > SECTION_LIMIT)
					break;
			}
			String id = heading.id();
			sections.add(new Section(id, title, level, List.copyOf(breadcrumb), source + "#" + id,
					text.substring(0, Math.min(text.length(), SECTION_LIMIT))));
		}
		return sections;
	}

	private record Section(String id, String title, int level, List<String> breadcrumb, String url, String text) {
	}

	private static String normalized(String value) {
		return value.replaceAll("[\\p{Z}\\s]+", " ").strip();
	}

	private static Response malformed(URI source) {
		return Response.status(502).entity(Map.of("status", "unavailable", "source", source.toString(), "message",
				"OpenJDK returned an unexpected Developers' Guide page. Its format may have changed.")).build();
	}

	private static Response invalid(String message) {
		return Response.status(400).entity(Map.of("status", "invalidArgument", "message", message)).build();
	}
}
