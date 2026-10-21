/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.mail;

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
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the public pipermail archives on mail.openjdk.org one month at a time; message bodies are
 * not fetched.
 */
@ApplicationScoped
public class MailingListService {
	private static final Pattern MESSAGE = Pattern.compile("^(\\d+)\\.html$");
	private static final Pattern COUNT = Pattern.compile("(?i)Messages:\\s*</b>\\s*(\\d+)");
	private static final Pattern NUMERIC_MONTH = Pattern.compile("(\\d{4})-(0[1-9]|1[0-2])");
	private static final Pattern NAMED_MONTH = Pattern.compile("(\\d{4})-([A-Z][a-z]+)");
	private static final Pattern ARCHIVE_MONTH = Pattern.compile("href=\"(\\d{4}-[A-Z][a-z]+)/thread\\.html\"");

	@Inject
	UpstreamHttp http;
	@ConfigProperty(name = "openjdk.mail.base-url", defaultValue = "https://mail.openjdk.org")
	String baseUrl = "https://mail.openjdk.org";

	public MailingListService() {
	}

	MailingListService(UpstreamHttp http, String baseUrl) {
		this.http = http;
		this.baseUrl = baseUrl;
	}

	public Response searchThreads(String list, String month, String query, Integer limit, Integer offset) {
		String listName;
		String archiveMonth;
		String text;
		int pageSize;
		int start;
		try {
			listName = listName(list);
			archiveMonth = month == null || month.isBlank() ? null : archiveMonth(month);
			text = filter(query);
			pageSize = ApiSupport.limit(limit);
			start = ApiSupport.offset(offset);
		} catch (WebApplicationException e) {
			return e.getResponse();
		} catch (IllegalArgumentException e) {
			return invalid(e.getMessage());
		}
		URI index = ApiSupport.uri(baseUrl, "/pipermail/" + listName + "/", Map.of());
		List<String> availableMonths = null;
		if (archiveMonth == null) {
			// Archives can lag the calendar, so the newest archived month is the useful default.
			var fetchedIndex = http.get(index, null);
			availableMonths = new ArrayList<>();
			Matcher months = ARCHIVE_MONTH.matcher(fetchedIndex.body());
			while (months.find() && availableMonths.size() < 24) {
				if (!availableMonths.contains(months.group(1)))
					availableMonths.add(months.group(1));
			}
			if (availableMonths.isEmpty())
				return malformed(index);
			archiveMonth = availableMonths.get(0);
		}
		URI source = ApiSupport.uri(baseUrl, "/pipermail/" + listName + "/" + archiveMonth + "/thread.html", Map.of());
		var fetched = http.get(source, null);
		Document page = Jsoup.parse(fetched.body(), source.toString());
		List<Map<String, Object>> messages = new ArrayList<>();
		int minimumDepth = Integer.MAX_VALUE;
		for (Element item : page.select("li")) {
			Element link = item.selectFirst("> a[href]");
			if (link == null)
				continue;
			Matcher id = MESSAGE.matcher(link.attr("href"));
			if (!id.matches())
				continue;
			String subject = normalized(link.text());
			if (subject.isEmpty())
				continue;
			Element author = item.selectFirst("> i");
			int depth = item.parents().stream().filter(parent -> parent.tagName().equals("ul")).toList().size();
			minimumDepth = Math.min(minimumDepth, depth);
			var message = new LinkedHashMap<String, Object>();
			message.put("id", Integer.parseInt(id.group(1)));
			message.put("subject", bounded(subject, 500));
			message.put("author", author == null ? "" : bounded(normalized(author.text()), 200));
			message.put("kind", kind(subject));
			message.put("depth", depth);
			message.put("url", link.absUrl("href"));
			messages.add(message);
		}
		if (messages.isEmpty() && !page.select("ul").isEmpty() && COUNT.matcher(fetched.body()).find()) {
			// Legitimate empty month.
		} else if (messages.isEmpty()) {
			return malformed(source);
		}
		for (var message : messages)
			message.put("depth", (int) message.get("depth") - minimumDepth);
		List<Map<String, Object>> matches = messages.stream()
				.filter(message -> text.isEmpty()
						|| message.get("subject").toString().toLowerCase(Locale.ROOT).contains(text)
						|| message.get("author").toString().toLowerCase(Locale.ROOT).contains(text))
				.toList();
		var results = matches.subList(Math.min(start, matches.size()), Math.min(start + pageSize, matches.size()));
		var result = new LinkedHashMap<String, Object>();
		result.put("status", "available");
		result.put("source", source.toString());
		result.put("fetchedAt", fetched.fetchedAt().toString());
		result.put("cached", fetched.cached());
		result.put("list", listName);
		result.put("archiveIndex", index.toString());
		result.put("month", archiveMonth);
		if (availableMonths != null)
			result.put("availableMonths", availableMonths);
		result.put("query", text);
		Matcher count = COUNT.matcher(fetched.body());
		result.put("messagesInMonth", count.find() ? Integer.parseInt(count.group(1)) : messages.size());
		result.put("results", results);
		result.put("offset", start);
		result.put("limit", pageSize);
		result.put("total", matches.size());
		result.put("hasMore", start + results.size() < matches.size());
		result.put("semantics",
				"Subjects and authors come from one month's thread index; depth 0 starts a thread. kind is derived from conventional subject prefixes (CFV, Result, RFR, RFC, Integrated, Withdrawn) and is a hint, not a vote tally. Read the linked message for content and vote outcomes.");
		return Response.ok(result).build();
	}

	static String listName(String value) {
		if (value == null || !value.matches("[a-z0-9][a-z0-9-]{0,60}")) {
			throw new IllegalArgumentException("list must be a mailing list name such as jmc-dev or jdk-dev");
		}
		return value;
	}

	static String archiveMonth(String value) {
		String month = value.strip();
		Matcher numeric = NUMERIC_MONTH.matcher(month);
		if (numeric.matches()) {
			return numeric.group(1) + "-"
					+ Month.of(Integer.parseInt(numeric.group(2))).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
		}
		Matcher named = NAMED_MONTH.matcher(month);
		if (named.matches()) {
			for (Month candidate : Month.values()) {
				if (candidate.getDisplayName(TextStyle.FULL, Locale.ENGLISH).equalsIgnoreCase(named.group(2))) {
					return named.group(1) + "-" + candidate.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
				}
			}
		}
		throw new IllegalArgumentException(
				"month must be YYYY-MM or YYYY-MonthName, for example 2026-02 or 2026-February");
	}

	static String kind(String subject) {
		String lower = subject.toLowerCase(Locale.ROOT).replaceFirst("^(re|fwd?):\\s*", "");
		if (lower.startsWith("cfv:"))
			return "vote";
		if (lower.startsWith("result:"))
			return "vote-result";
		if (lower.startsWith("rfr:"))
			return "review-request";
		if (lower.startsWith("rfc:"))
			return "request-for-comments";
		if (lower.startsWith("integrated:"))
			return "integrated";
		if (lower.startsWith("withdrawn:"))
			return "withdrawn";
		if (lower.startsWith("[rfr]") || lower.startsWith("rfr "))
			return "review-request";
		return "other";
	}

	private static String filter(String value) {
		if (value == null)
			return "";
		if (value.length() > 200 || value.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("query must have at most 200 characters and no control characters");
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
		return Response.status(502).entity(Map.of("status", "unavailable", "source", source.toString(), "message",
				"The mailing list archive returned an unexpected page. Its format may have changed.")).build();
	}

	private static Response invalid(String message) {
		return Response.status(400).entity(Map.of("status", "invalidArgument", "message", message)).build();
	}
}
