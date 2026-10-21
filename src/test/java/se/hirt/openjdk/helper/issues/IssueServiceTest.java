/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.issues;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.hirt.openjdk.helper.common.UpstreamHttp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class IssueServiceTest {
	private final ObjectMapper json = new ObjectMapper();
	private final AtomicInteger calls = new AtomicInteger();
	private final AtomicReference<URI> requested = new AtomicReference<>();
	private final AtomicReference<String> authorization = new AtomicReference<>();
	private HttpServer server;
	private UpstreamHttp http;
	private IssueService service;
	private volatile String responseBody;
	private final Map<String, String> bodies = new java.util.concurrent.ConcurrentHashMap<>();
	private volatile int responseStatus;
	private volatile String retryAfter;
	private String base;

	@BeforeEach
	void start() throws IOException {
		responseStatus = 200;
		responseBody = fixture("issue.json");
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			calls.incrementAndGet();
			requested.set(exchange.getRequestURI());
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			byte[] bytes = bodies.getOrDefault(exchange.getRequestURI().getPath(), responseBody)
					.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			if (retryAfter != null)
				exchange.getResponseHeaders().set("Retry-After", retryAfter);
			exchange.sendResponseHeaders(responseStatus, bytes.length);
			try (var body = exchange.getResponseBody()) {
				body.write(bytes);
			}
		});
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
		http = new UpstreamHttp();
		service = new IssueService(http, json, base, "approved-fixture-token");
	}

	@AfterEach
	void stop() {
		if (http != null)
			http.close();
		if (server != null)
			server.stop(0);
	}

	@Test
	void exposesIssueVersionsAndDirectedCsrAndBackportLinks() {
		Response response = service.getIssue(" jdk-8291976 ");
		assertEquals(200, response.getStatus());
		JsonNode data = data(response);
		assertEquals("available", data.path("status").asText());
		assertTrue(data.path("source").asText().startsWith(base + "/rest/api/2/issue/JDK-8291976?"));
		assertFalse(data.path("fetchedAt").asText().isBlank());
		JsonNode issue = data.path("issue");
		assertEquals("JDK-8291976", issue.path("key").asText());
		assertEquals(base + "/browse/JDK-8291976", issue.path("url").asText());
		assertEquals("26", issue.path("fixVersions").path(0).asText());
		assertEquals("25", issue.path("affectedVersions").path(0).asText());
		assertEquals("has csr", issue.path("relatedIssues").path(0).path("relationship").asText());
		assertEquals("CSR", issue.path("relatedIssues").path(0).path("type").asText());
		assertEquals("backported by", issue.path("relatedIssues").path(1).path("relationship").asText());
		assertFalse(issue.path("descriptionTruncated").asBoolean());
		assertEquals("Bearer approved-fixture-token", authorization.get());
		assertEquals(1, calls.get(), "Linked issues should not trigger unbounded follow-up requests");
	}

	@Test
	void rejectsIncompleteOrUnexpectedIssueKeysBeforeFetching() {
		for (String key : new String[] {"8291976", "OTHER-123", "JDK-0", "JDK-1/../../search", "JMC-12?fields=all",
				""}) {
			assertEquals(400, service.getIssue(key).getStatus(), key);
		}
		assertEquals(400, service.getIssue(null).getStatus());
		assertEquals("CODETOOLS-7900000", IssueService.issueKey("codetools-7900000"));
		assertEquals(0, calls.get());
	}

	@Test
	void pagesSearchResultsAndKeepsUserValuesInsideJqlLiterals() throws IOException {
		responseBody = fixture("search.json");
		String query = "compiler\" OR project = SECRET OR text ~ \"anything\\";
		String status = "Open\" OR assignee is not EMPTY OR status = \"Open";
		Response response = service.searchIssues(query, "jmc", status, 2, 20);
		assertEquals(200, response.getStatus());
		JsonNode data = data(response);
		assertEquals(2, data.path("results").size());
		assertEquals("JMC-8639", data.path("results").path(0).path("key").asText());
		assertEquals(23, data.path("total").asInt());
		assertTrue(data.path("hasMore").asBoolean());
		Map<String, String> params = parameters(requested.get());
		assertEquals("20", params.get("startAt"));
		assertEquals("2", params.get("maxResults"));
		assertFalse(params.get("fields").contains("description"));
		assertEquals("project = \"\" AND text ~ \"\" AND status = \"\" ORDER BY updated DESC, key ASC",
				withoutLiteralContents(params.get("jql")));
		assertTrue(params.get("jql").startsWith("project = \"JMC\" AND text ~ "));
		assertFalse(data.path("results").path(0).has("description"));
	}

	@Test
	void structuredFiltersBecomeQuotedJqlClauses() throws IOException {
		responseBody = fixture("search.json");
		var filters = new IssueService.Filters(null, "JDK", null, "CSR\" OR project = SECRET", "26", "hotspot", "P2",
				"unassigned", "unresolved", "2026-07-01", "2026-07-15");
		assertEquals(200, service.searchIssues(filters, null, null).getStatus());
		String jql = parameters(requested.get()).get("jql");
		assertEquals(
				"project = \"\" AND issuetype = \"\" AND fixVersion = \"\" AND component = \"\" AND priority = \"\""
						+ " AND assignee is EMPTY AND resolution is EMPTY AND updated >= \"\" AND resolved >= \"\" ORDER BY updated DESC, key ASC",
				withoutLiteralContents(jql));
		assertTrue(jql.contains("issuetype = \"CSR\\\" OR project = SECRET\""));
		assertTrue(jql.contains("updated >= \"2026-07-01\""));
		JsonNode data = data(service.searchIssues(
				new IssueService.Filters(null, null, null, null, null, null, null, "duke", "Fixed", null, null), null,
				null));
		assertTrue(data.path("jql").asText().contains("assignee = \"duke\" AND resolution = \"Fixed\""));
		assertTrue(data.path("jql").asText().startsWith("project in (\"JDK\", \"JMC\", \"CODETOOLS\", \"SKARA\")"));
		assertEquals(400, service.searchIssues(
				new IssueService.Filters(null, null, null, null, null, null, null, null, null, "2026-13-01", null),
				null, null).getStatus());
		assertEquals(400, service.searchIssues(
				new IssueService.Filters(null, null, null, null, null, null, null, null, null, "July 2026", null), null,
				null).getStatus());
	}

	@Test
	void fixStatusCombinesTheIssueWithItsLinkedBackports() throws IOException {
		bodies.put("/rest/api/2/issue/JDK-8291976", fixture("issue.json"));
		bodies.put("/rest/api/2/search", fixture("backports.json"));
		Response response = service.getIssueFixStatus("jdk-8291976");
		assertEquals(200, response.getStatus());
		JsonNode data = data(response);
		assertEquals("JDK-8291976", data.path("issue").path("key").asText());
		assertFalse(data.path("issue").has("description"));
		assertEquals(2, data.path("backports").size());
		assertEquals(2, data.path("backportTotal").asInt());
		assertFalse(data.path("backportsTruncated").asBoolean());
		assertEquals("JDK-8394124", data.path("backports").get(0).path("key").asText());
		assertEquals("17.0.22", data.path("backports").get(0).path("fixVersions").get(0).asText());
		assertEquals(List.of("17.0.22", "26"),
				List.of(data.path("fixedIn").get(0).asText(), data.path("fixedIn").get(1).asText()));
		assertEquals("11.0.34", data.path("pendingIn").get(0).asText());
		Map<String, String> params = parameters(requested.get());
		assertEquals("issuetype = Backport AND issue in linkedIssues(\"JDK-8291976\") ORDER BY key ASC",
				params.get("jql"));
		assertEquals("50", params.get("maxResults"));
		assertEquals(2, calls.get());
		assertEquals(400, service.getIssueFixStatus("8291976").getStatus());
		assertEquals("SKARA-2000", IssueService.issueKey("skara-2000"));
	}

	@Test
	void rejectsInvalidSearchFiltersAndBounds() {
		assertEquals(400, service.searchIssues(null, "SECRET", null, null, null).getStatus());
		assertEquals(400, service.searchIssues("a\nb", null, null, null, null).getStatus());
		assertEquals(400, service.searchIssues(null, null, null, 101, null).getStatus());
		assertEquals(400, service.searchIssues(null, null, null, 0, null).getStatus());
		assertEquals(400, service.searchIssues(null, null, null, null, -1).getStatus());
		assertEquals(400, service.searchIssues(null, null, null, null, 10001).getStatus());
		assertEquals(0, calls.get());
	}

	@Test
	void deniedAccessIsExplicitAndNeverAnEmptySuccessfulSearch() {
		responseStatus = 403;
		responseBody = "Access denied";
		for (Response response : new Response[] {service.getIssue("JDK-8291976"),
				service.searchIssues("compiler", null, null, null, null)}) {
			assertEquals(503, response.getStatus());
			JsonNode error = data(response);
			assertEquals("unavailable", error.path("status").asText());
			assertTrue(error.path("accessDenied").asBoolean());
			assertEquals(403, error.path("upstreamStatus").asInt());
			assertTrue(error.path("message").asText().contains("not guaranteed"));
			assertTrue(error.path("source").asText().startsWith(base));
			assertFalse(error.has("results"));
			assertFalse(error.toString().contains("approved-fixture-token"));
		}
	}

	@Test
	void absentIssuePreservesNotFound() {
		responseStatus = 404;
		Response response = service.getIssue("JMC-9999999");
		assertEquals(404, response.getStatus());
		assertEquals("notFound", data(response).path("status").asText());
	}

	@Test
	void rateLimitPreservesStatusAndRetryHint() {
		responseStatus = 429;
		retryAfter = "60";
		Response response = service.getIssue("JMC-8639");
		assertEquals(429, response.getStatus());
		assertEquals("rateLimited", data(response).path("status").asText());
		assertEquals("60", data(response).path("retryAfter").asText());
		assertFalse(data(response).path("accessDenied").asBoolean());
	}

	@Test
	void malformedSuccessPayloadIsReportedAsUpstreamFailure() {
		responseBody = "<html><h1>Access denied</h1></html>";
		assertEquals(502, service.getIssue("JDK-8291976").getStatus());
		assertEquals(502, service.searchIssues(null, null, null, null, null).getStatus());
	}

	@Test
	void boundsLongDescriptions() throws IOException {
		var document = json.readTree(responseBody);
		((com.fasterxml.jackson.databind.node.ObjectNode) document.path("fields")).put("description", "x".repeat(7000));
		responseBody = document.toString();
		JsonNode issue = data(service.getIssue("JDK-8291976")).path("issue");
		assertEquals(6000, issue.path("description").asText().length());
		assertTrue(issue.path("descriptionTruncated").asBoolean());
	}

	private JsonNode data(Response response) {
		return json.valueToTree(response.getEntity());
	}

	private static String fixture(String name) throws IOException {
		try (var input = IssueServiceTest.class.getResourceAsStream("/issues/" + name)) {
			if (input == null)
				throw new IOException("Missing fixture " + name);
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static Map<String, String> parameters(URI uri) {
		var params = new HashMap<String, String>();
		for (String part : uri.getRawQuery().split("&")) {
			String[] pair = part.split("=", 2);
			params.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
					URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
		}
		return params;
	}

	/** Inspect the grammar outside quoted values, so injected operators cannot become JQL. */
	private static String withoutLiteralContents(String jql) {
		var outside = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < jql.length(); i++) {
			char ch = jql.charAt(i);
			if (quoted && ch == '\\') {
				i++;
				continue;
			}
			if (ch == '"') {
				quoted = !quoted;
				outside.append(ch);
			} else if (!quoted)
				outside.append(ch);
		}
		assertFalse(quoted, "JQL literals must be balanced");
		return outside.toString();
	}
}
