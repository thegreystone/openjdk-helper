/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.jeps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.hirt.openjdk.helper.common.UpstreamHttp;
import se.hirt.openjdk.helper.common.UpstreamException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JepServiceTest {
	private final ObjectMapper json = new ObjectMapper();
	private final AtomicInteger calls = new AtomicInteger();
	private HttpServer server;
	private UpstreamHttp http;
	private JepService service;
	private String base;
	private volatile String index;
	private volatile String detail;
	private volatile int responseStatus;

	@BeforeEach
	void start() throws IOException {
		index = fixture("index.html");
		detail = fixture("517.html");
		responseStatus = 200;
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/jeps/", exchange -> {
			calls.incrementAndGet();
			String body = exchange.getRequestURI().getPath().equals("/jeps/0") ? index : detail;
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
			exchange.sendResponseHeaders(responseStatus, bytes.length);
			try (var out = exchange.getResponseBody()) { out.write(bytes); }
		});
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
		http = new UpstreamHttp();
		service = new JepService(http, base);
	}

	@AfterEach
	void stop() {
		if (http != null) http.close();
		if (server != null) server.stop(0);
	}

	@Test
	void readsPublishedMetadataSummaryAndSourceLinks() {
		Response response = service.getJep(517);
		assertEquals(200, response.getStatus());
		JsonNode data = data(response);
		assertEquals(base + "/jeps/517", data.path("source").asText());
		assertFalse(data.path("fetchedAt").asText().isBlank());
		JsonNode jep = data.path("jep");
		assertEquals(517, jep.path("number").asInt());
		assertEquals("HTTP/3 for the HTTP Client API", jep.path("title").asText());
		assertEquals("Closed / Delivered", jep.path("status").asText());
		assertEquals("26", jep.path("release").asText());
		assertEquals("core-libs / java.net", jep.path("component").asText());
		assertEquals("Daniel Fuchs", jep.path("owner").asText());
		assertTrue(jep.path("summary").asText().contains("minimal code changes"));
		assertFalse(jep.path("summary").asText().contains("belongs to the full"));
		assertFalse(jep.path("summaryTruncated").asBoolean());
		assertEquals(base + "/jeps/321", jep.path("links").path(0).path("url").asText());
		assertEquals("https://bugs.openjdk.org/browse/JDK-8291976", jep.path("links").path(1).path("url").asText());
	}

	@Test
	void filtersIndexByTextStatusComponentAndRelease() {
		Response response = service.searchJeps("http", "delivered", "26", 10, 0);
		assertEquals(200, response.getStatus());
		JsonNode page = data(response);
		assertEquals(1, page.path("total").asInt());
		assertEquals(517, page.path("results").path(0).path("number").asInt());
		assertFalse(page.path("hasMore").asBoolean());
		assertEquals(321, data(service.searchJeps("321", null, null, null, null)).path("results").path(0).path("number").asInt());
	}

	@Test
	void preservesMissingReleaseAndDraftIdsAndPaginates() {
		JsonNode first = data(service.searchJeps(null, null, null, 2, 0));
		assertEquals(5, first.path("total").asInt());
		assertEquals(2, first.path("results").size());
		assertTrue(first.path("hasMore").asBoolean());
		assertFalse(first.path("results").path(0).has("release"));
		JsonNode last = data(service.searchJeps(null, null, null, 2, 4));
		assertEquals(8377081, last.path("results").path(0).path("number").asInt());
		assertFalse(last.path("results").path(0).has("release"));
		assertFalse(last.path("hasMore").asBoolean());
		assertEquals(0, data(service.searchJeps(null, null, null, 10, 100)).path("results").size());
	}

	@Test
	void validEmptyFilterResultIsDistinctFromMalformedUpstreamPage() {
		JsonNode empty = data(service.searchJeps("does not exist", null, null, null, null));
		assertEquals("available", empty.path("status").asText());
		assertEquals(0, empty.path("total").asInt());
		detail = "<html><h1>Access denied</h1></html>";
		Response malformed = service.getJep(517);
		assertEquals(502, malformed.getStatus());
		assertEquals("unavailable", data(malformed).path("status").asText());
	}

	@Test
	void rejectsMalformedIndexRatherThanReportingNoJeps() {
		index = "<html><h1>Access denied</h1></html>";
		Response response = service.searchJeps(null, null, null, null, null);
		assertEquals(502, response.getStatus());
		assertFalse(data(response).has("results"));
	}

	@Test
	void rejectsBadArgumentsBeforeRequest() {
		assertEquals(400, service.getJep(-1).getStatus());
		assertEquals(400, service.getJep(10_000_000).getStatus());
		assertEquals(400, service.searchJeps("x\ny", null, null, null, null).getStatus());
		assertEquals(400, service.searchJeps(null, null, null, 101, null).getStatus());
		assertEquals(400, service.searchJeps(null, null, null, null, -1).getStatus());
		assertEquals(400, service.searchJeps(null, null, null, null, 10001).getStatus());
		assertEquals(0, calls.get());
	}

	@Test
	void boundsSummaryAndDoesNotInventMetadata() {
		detail = "<h1>JEP draft: Example</h1><table class='head'><tr><td>Type</td><td>Feature</td></tr><tr><td>Status</td><td>Submitted</td></tr></table>"
				+ "<h2 id='Summary'>Summary</h2><p>" + "x".repeat(5000) + "</p><h2>Details</h2><p>Excluded</p>";
		JsonNode jep = data(service.getJep(8377081)).path("jep");
		assertTrue(jep.path("draft").asBoolean());
		assertEquals(4000, jep.path("summary").asText().length());
		assertTrue(jep.path("summaryTruncated").asBoolean());
		assertFalse(jep.has("release"));
		assertFalse(jep.has("owner"));
	}

	@Test
	void propagatesUpstreamErrorsForSharedRestAndToolMapping() {
		responseStatus = 503;
		UpstreamException error = assertThrows(UpstreamException.class, () -> service.getJep(517));
		assertEquals(503, error.status());
	}

	private JsonNode data(Response response) { return json.valueToTree(response.getEntity()); }

	private static String fixture(String name) throws IOException {
		try (var input = JepServiceTest.class.getResourceAsStream("/jeps/" + name)) {
			if (input == null) throw new IOException("Missing fixture " + name);
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
