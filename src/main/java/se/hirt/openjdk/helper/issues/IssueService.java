/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.issues;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import se.hirt.openjdk.helper.common.ApiSupport;
import se.hirt.openjdk.helper.common.UpstreamException;
import se.hirt.openjdk.helper.common.UpstreamHttp;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads the public Jira REST API without assuming that the upstream permits anonymous access. */
@ApplicationScoped
public class IssueService {

	private static final Set<String> PROJECTS = Set.of("JDK", "JMC", "CODETOOLS", "SKARA");
	private static final String FIELDS = "summary,status,resolution,issuetype,priority,description,assignee,"
			+ "components,labels,versions,fixVersions,issuelinks,created,updated,resolutiondate";
	private static final String BACKPORT_FIELDS = "summary,status,resolution,issuetype,fixVersions,versions,updated,resolutiondate";
	private static final int DESCRIPTION_LIMIT = 6000;
	private static final int BACKPORT_LIMIT = 50;

	/** Structured search filters; every value is a literal, quoted into JQL. */
	public record Filters(String query, String project, String status, String type, String fixVersion, String component,
			String priority, String assignee, String resolution, String updatedSince, String resolvedSince) {
		public static Filters of(String query, String project, String status) {
			return new Filters(query, project, status, null, null, null, null, null, null, null, null);
		}
	}

	@Inject
	UpstreamHttp http;
	@Inject
	ObjectMapper json;
	@ConfigProperty(name = "openjdk.jbs.base-url", defaultValue = "https://bugs.openjdk.org")
	String baseUrl = "https://bugs.openjdk.org";
	@ConfigProperty(name = "openjdk.jbs.token")
	Optional<String> token = Optional.empty();

	public IssueService() {
	}

	IssueService(UpstreamHttp http, ObjectMapper json, String baseUrl, String token) {
		this.http = http;
		this.json = json;
		this.baseUrl = baseUrl;
		this.token = Optional.ofNullable(token);
	}

	public Response getIssue(String key) {
		String normalized;
		try {
			normalized = issueKey(key);
		} catch (IllegalArgumentException e) {
			return invalid(e.getMessage());
		}
		URI source = ApiSupport.uri(baseUrl, "/rest/api/2/issue/" + normalized, Map.of("fields", FIELDS));
		try {
			var fetched = http.get(source, token.orElse(null));
			JsonNode issue = json.readTree(fetched.body());
			if (issue == null || !normalized.equals(issue.path("key").asText()) || !issue.path("fields").isObject()) {
				return malformed(source);
			}
			return Response.ok(Map.of("status", "available", "source", source.toString(),
					"fetchedAt", fetched.fetchedAt().toString(), "cached", fetched.cached(),
					"issue", summarize(issue, true))).build();
		} catch (UpstreamException e) {
			return unavailable(source, e);
		} catch (JsonProcessingException e) {
			return malformed(source);
		}
	}

	public Response searchIssues(String query, String project, String status, Integer limit, Integer offset) {
		return searchIssues(Filters.of(query, project, status), limit, offset);
	}

	public Response searchIssues(Filters filters, Integer limit, Integer offset) {
		String jql;
		int pageSize;
		int start;
		try {
			pageSize = ApiSupport.limit(limit);
			start = ApiSupport.offset(offset);
			jql = searchJql(filters);
		} catch (WebApplicationException e) {
			return e.getResponse();
		} catch (IllegalArgumentException e) {
			return invalid(e.getMessage());
		}
		URI source = ApiSupport.uri(baseUrl, "/rest/api/2/search", Map.of("jql", jql,
				"startAt", start, "maxResults", pageSize, "fields", FIELDS.replace("description,", "")));
		try {
			var fetched = http.get(source, token.orElse(null));
			JsonNode page = json.readTree(fetched.body());
			if (page == null || !page.path("issues").isArray() || !page.path("total").canConvertToInt()
					|| page.path("total").asInt() < 0) return malformed(source);
			var results = new ArrayList<Map<String, Object>>();
			for (JsonNode issue : page.path("issues")) {
				if (!issue.path("key").isTextual() || !issue.path("fields").isObject()) return malformed(source);
				if (results.size() < pageSize) results.add(summarize(issue, false));
			}
			int total = page.path("total").asInt();
			var result = new LinkedHashMap<String, Object>();
			result.put("status", "available");
			result.put("source", source.toString());
			result.put("fetchedAt", fetched.fetchedAt().toString());
			result.put("cached", fetched.cached());
			result.put("jql", jql);
			result.put("results", results);
			result.put("offset", start);
			result.put("limit", pageSize);
			result.put("total", total);
			result.put("hasMore", start + results.size() < total);
			return Response.ok(result).build();
		} catch (UpstreamException e) {
			return unavailable(source, e);
		} catch (JsonProcessingException e) {
			return malformed(source);
		}
	}

	/** The main issue plus every linked Backport issue with its fix versions, in two bounded requests. */
	public Response getIssueFixStatus(String key) {
		String normalized;
		try {
			normalized = issueKey(key);
		} catch (IllegalArgumentException e) {
			return invalid(e.getMessage());
		}
		URI source = ApiSupport.uri(baseUrl, "/rest/api/2/issue/" + normalized, Map.of("fields", FIELDS.replace("description,", "")));
		String jql = "issuetype = Backport AND issue in linkedIssues(" + quoted(normalized) + ") ORDER BY key ASC";
		URI backportSource = ApiSupport.uri(baseUrl, "/rest/api/2/search", Map.of("jql", jql, "startAt", 0,
				"maxResults", BACKPORT_LIMIT, "fields", BACKPORT_FIELDS));
		try {
			var fetched = http.get(source, token.orElse(null));
			JsonNode issue = json.readTree(fetched.body());
			if (issue == null || !normalized.equals(issue.path("key").asText()) || !issue.path("fields").isObject()) {
				return malformed(source);
			}
			Map<String, Object> main = summarize(issue, false);
			var fetchedBackports = http.get(backportSource, token.orElse(null));
			JsonNode page = json.readTree(fetchedBackports.body());
			if (page == null || !page.path("issues").isArray() || !page.path("total").canConvertToInt()) return malformed(backportSource);
			var backports = new ArrayList<Map<String, Object>>();
			var fixedIn = new java.util.TreeSet<String>();
			var pendingIn = new java.util.TreeSet<String>();
			record(main, fixedIn, pendingIn);
			for (JsonNode backport : page.path("issues")) {
				if (!backport.path("key").isTextual() || !backport.path("fields").isObject()) return malformed(backportSource);
				Map<String, Object> entry = summarize(backport, false);
				entry.remove("relatedIssues");
				entry.remove("components");
				entry.remove("labels");
				record(entry, fixedIn, pendingIn);
				backports.add(entry);
			}
			var result = new LinkedHashMap<String, Object>();
			result.put("status", "available");
			result.put("source", source.toString());
			result.put("backportSource", backportSource.toString());
			result.put("fetchedAt", fetchedBackports.fetchedAt().toString());
			result.put("cached", fetched.cached() && fetchedBackports.cached());
			result.put("issue", main);
			if ("Backport".equalsIgnoreCase(String.valueOf(main.get("type")))) {
				for (var related : (List<Map<String, Object>>) main.get("relatedIssues")) {
					if ("backport of".equalsIgnoreCase(String.valueOf(related.get("relationship")))) result.put("mainIssue", related.get("key"));
				}
				result.put("hint", "This is a Backport issue; query mainIssue to see every release.");
			}
			result.put("backports", backports);
			result.put("backportTotal", page.path("total").asInt());
			result.put("backportsTruncated", page.path("total").asInt() > backports.size());
			result.put("fixedIn", new ArrayList<>(fixedIn));
			result.put("pendingIn", new ArrayList<>(pendingIn));
			result.put("semantics", "fixedIn lists fix versions of the issue and its linked Backport issues whose resolution is Fixed; pendingIn lists fix versions of unresolved ones. A Fixed resolution means the change was pushed to that release's repository, not that a build shipped. Backports are linked by JBS, so a missing link hides a release.");
			return Response.ok(result).build();
		} catch (UpstreamException e) {
			return unavailable(source, e);
		} catch (JsonProcessingException e) {
			return malformed(source);
		}
	}

	@SuppressWarnings("unchecked")
	private static void record(Map<String, Object> issue, Set<String> fixedIn, Set<String> pendingIn) {
		List<String> versions = (List<String>) issue.getOrDefault("fixVersions", List.of());
		boolean fixed = "Fixed".equalsIgnoreCase(String.valueOf(issue.get("resolution")));
		boolean unresolved = issue.get("resolution") == null;
		for (String version : versions) {
			if (fixed) fixedIn.add(version);
			else if (unresolved) pendingIn.add(version);
		}
	}

	static String issueKey(String value) {
		if (value == null) throw new IllegalArgumentException("A full JBS key is required, for example JDK-8344154");
		String key = value.strip().toUpperCase(Locale.ROOT);
		if (!key.matches("(?:JDK|JMC|CODETOOLS|SKARA)-[1-9][0-9]{0,8}")) {
			throw new IllegalArgumentException("Use a full JDK, JMC, CODETOOLS or SKARA issue key, for example JMC-8639");
		}
		return key;
	}

	static String searchJql(String query, String project, String status) {
		return searchJql(Filters.of(query, project, status));
	}

	static String searchJql(Filters filters) {
		String projectClause = "project in (\"JDK\", \"JMC\", \"CODETOOLS\", \"SKARA\")";
		if (filters.project() != null && !filters.project().isBlank()) {
			String name = filters.project().strip().toUpperCase(Locale.ROOT);
			if (!PROJECTS.contains(name)) throw new IllegalArgumentException("project must be JDK, JMC, CODETOOLS or SKARA");
			projectClause = "project = " + quoted(name);
		}
		var jql = new StringBuilder(projectClause);
		if (filters.query() != null && !filters.query().isBlank()) {
			String text = boundedArgument(filters.query(), "query", 300);
			// Jira has two grammars here: escape Lucene phrase syntax, then quote that value for JQL.
			var phrase = new StringBuilder("\"");
			for (char ch : text.toCharArray()) {
				if ("+-!(){}[]^\"~*?:\\/&|".indexOf(ch) >= 0) phrase.append('\\');
				phrase.append(ch);
			}
			phrase.append('"');
			jql.append(" AND text ~ ").append(quoted(phrase.toString()));
		}
		literal(jql, "status", filters.status(), "status");
		literal(jql, "issuetype", filters.type(), "type");
		literal(jql, "fixVersion", filters.fixVersion(), "fixVersion");
		literal(jql, "component", filters.component(), "component");
		literal(jql, "priority", filters.priority(), "priority");
		if (filters.assignee() != null && !filters.assignee().isBlank()) {
			String assignee = boundedArgument(filters.assignee(), "assignee", 80);
			jql.append(assignee.equalsIgnoreCase("unassigned") ? " AND assignee is EMPTY" : " AND assignee = " + quoted(assignee));
		}
		if (filters.resolution() != null && !filters.resolution().isBlank()) {
			String resolution = boundedArgument(filters.resolution(), "resolution", 80);
			jql.append(resolution.equalsIgnoreCase("unresolved") ? " AND resolution is EMPTY" : " AND resolution = " + quoted(resolution));
		}
		date(jql, "updated", filters.updatedSince(), "updatedSince");
		date(jql, "resolved", filters.resolvedSince(), "resolvedSince");
		return jql.append(" ORDER BY updated DESC, key ASC").toString();
	}

	private static void literal(StringBuilder jql, String field, String value, String name) {
		if (value != null && !value.isBlank()) jql.append(" AND ").append(field).append(" = ").append(quoted(boundedArgument(value, name, 80)));
	}

	private static void date(StringBuilder jql, String field, String value, String name) {
		if (value == null || value.isBlank()) return;
		String date = value.strip();
		if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException(name + " must be a date formatted as YYYY-MM-DD");
		try {
			java.time.LocalDate.parse(date);
		} catch (java.time.format.DateTimeParseException e) {
			throw new IllegalArgumentException(name + " is not a valid date");
		}
		jql.append(" AND ").append(field).append(" >= ").append(quoted(date));
	}

	private static String quoted(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static String boundedArgument(String value, String name, int maxLength) {
		String text = value.strip();
		if (text.length() > maxLength || text.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException(name + " must have at most " + maxLength + " characters and no control characters");
		}
		return text;
	}

	private Map<String, Object> summarize(JsonNode issue, boolean includeDescription) {
		var result = new LinkedHashMap<String, Object>();
		String key = issue.path("key").asText();
		JsonNode fields = issue.path("fields");
		result.put("key", key);
		result.put("url", ApiSupport.uri(baseUrl, "/browse/" + ApiSupport.segment(key), Map.of()).toString());
		copyText(result, "summary", fields.path("summary"));
		copyText(result, "status", fields.path("status").path("name"));
		copyText(result, "resolution", fields.path("resolution").path("name"));
		copyText(result, "type", fields.path("issuetype").path("name"));
		copyText(result, "priority", fields.path("priority").path("name"));
		copyText(result, "assignee", fields.path("assignee").path("displayName"));
		copyText(result, "created", fields.path("created"));
		copyText(result, "updated", fields.path("updated"));
		copyText(result, "resolved", fields.path("resolutiondate"));
		result.put("affectedVersions", names(fields.path("versions")));
		result.put("fixVersions", names(fields.path("fixVersions")));
		result.put("components", names(fields.path("components")));
		result.put("labels", names(fields.path("labels")));
		if (includeDescription && !fields.path("description").isNull() && !fields.path("description").isMissingNode()) {
			JsonNode node = fields.path("description");
			String description = node.isTextual() ? node.asText() : node.toString();
			result.put("description", description.substring(0, Math.min(description.length(), DESCRIPTION_LIMIT)));
			result.put("descriptionTruncated", description.length() > DESCRIPTION_LIMIT);
		}
		var links = new ArrayList<Map<String, Object>>();
		for (JsonNode link : fields.path("issuelinks")) {
			boolean outward = link.has("outwardIssue");
			JsonNode related = link.path(outward ? "outwardIssue" : "inwardIssue");
			if (!related.path("key").isTextual()) continue;
			var relation = new LinkedHashMap<String, Object>();
			relation.put("key", related.path("key").asText());
			relation.put("url", ApiSupport.uri(baseUrl, "/browse/" + ApiSupport.segment(related.path("key").asText()), Map.of()).toString());
			copyText(relation, "relationship", link.path("type").path(outward ? "outward" : "inward"));
			copyText(relation, "linkType", link.path("type").path("name"));
			copyText(relation, "summary", related.path("fields").path("summary"));
			copyText(relation, "status", related.path("fields").path("status").path("name"));
			copyText(relation, "type", related.path("fields").path("issuetype").path("name"));
			links.add(relation);
		}
		result.put("relatedIssues", links);
		return result;
	}

	private static List<String> names(JsonNode values) {
		var result = new ArrayList<String>();
		for (JsonNode value : values) {
			String name = value.isTextual() ? value.asText() : value.path("name").asText();
			if (!name.isBlank()) result.add(name);
		}
		return result;
	}

	private static void copyText(Map<String, Object> target, String key, JsonNode value) {
		if (value.isTextual() && !value.asText().isBlank()) target.put(key, value.asText());
	}

	private static Response unavailable(URI source, UpstreamException exception) {
		int status = exception.status();
		boolean denied = status == 401 || status == 403;
		String message = denied
				? "JBS denied this request. Check access to the source URL from the server's network and the JBS access policy. "
						+ "An approved token can be configured with openjdk.jbs.token if your JBS deployment supports it; authentication is not guaranteed to resolve a 403."
				: status == 404 ? "JBS did not expose this issue. It may not exist or may not be visible to this client."
				: status == 429 ? "JBS rate-limited this request. Wait before retrying and honor any supplied retry hints."
				: "JBS is unavailable for this request. Retry later and check the source URL and upstream status.";
		var error = new LinkedHashMap<String, Object>();
		error.put("status", status == 404 ? "notFound" : status == 429 ? "rateLimited" : "unavailable");
		error.put("accessDenied", denied);
		error.put("upstreamStatus", exception.upstreamStatus());
		error.put("source", source.toString());
		error.put("message", message);
		Map<String, Object> upstream = exception.error();
		for (String hint : List.of("retryAfter", "rateLimitReset", "rateLimitRemaining")) {
			if (upstream.containsKey(hint)) error.put(hint, upstream.get(hint));
		}
		return Response.status(status == 404 || status == 429 || status == 504 ? status : 503).entity(error).build();
	}

	private static Response malformed(URI source) {
		return Response.status(502).entity(Map.of("status", "unavailable", "source", source.toString(),
				"message", "JBS returned an unexpected response instead of issue data.")).build();
	}

	private static Response invalid(String message) {
		return Response.status(400).entity(Map.of("status", "invalidArgument", "message", message)).build();
	}
}
