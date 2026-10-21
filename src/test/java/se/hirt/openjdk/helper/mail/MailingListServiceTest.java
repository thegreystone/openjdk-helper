/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.hirt.openjdk.helper.common.UpstreamException;
import se.hirt.openjdk.helper.common.UpstreamHttp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class MailingListServiceTest {
	private final ObjectMapper json = new ObjectMapper();
	private final List<String> requests = new CopyOnWriteArrayList<>();
	private HttpServer server;
	private UpstreamHttp http;
	private MailingListService service;
	private volatile String page;
	private volatile String index;

	@BeforeEach
	void start() throws IOException {
		page = fixture("/mail/thread.html");
		index = fixture("/mail/index.html");
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/pipermail/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			requests.add(path);
			boolean known = path.equals("/pipermail/jmc-dev/2026-February/thread.html") || path.endsWith("/");
			byte[] bytes = (path.endsWith("/") ? index : known ? page : "").getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=us-ascii");
			exchange.sendResponseHeaders(known ? 200 : 404, known ? bytes.length : -1);
			try (var out = exchange.getResponseBody()) { out.write(bytes); }
		});
		server.start();
		http = new UpstreamHttp();
		service = new MailingListService(http, "http://127.0.0.1:" + server.getAddress().getPort());
	}

	@AfterEach
	void stop() {
		if (http != null) http.close();
		if (server != null) server.stop(0);
	}

	@Test
	void listsThreadsWithDepthAndKinds() {
		JsonNode data = data(service.searchThreads("jmc-dev", "2026-02", null, null, null));
		assertEquals("available", data.path("status").asText());
		assertEquals("2026-February", data.path("month").asText());
		assertEquals(4, data.path("messagesInMonth").asInt());
		assertEquals(4, data.path("total").asInt());
		JsonNode vote = data.path("results").get(1);
		assertEquals("vote", vote.path("kind").asText());
		assertEquals(0, vote.path("depth").asInt());
		assertEquals("Marcus Hirt", vote.path("author").asText());
		assertTrue(vote.path("url").asText().endsWith("/pipermail/jmc-dev/2026-February/006994.html"));
		JsonNode reply = data.path("results").get(2);
		assertEquals(1, reply.path("depth").asInt());
		assertEquals("vote", reply.path("kind").asText());
		assertEquals("review-request", data.path("results").get(3).path("kind").asText());
		assertEquals("integrated", data.path("results").get(0).path("kind").asText());
	}

	@Test
	void filtersBySubjectOrAuthorAndPaginates() {
		JsonNode data = data(service.searchThreads("jmc-dev", "2026-February", "cfv", 1, 1));
		assertEquals(2, data.path("total").asInt());
		assertEquals(1, data.path("results").size());
		assertFalse(data.path("hasMore").asBoolean());
		assertEquals(6995, data.path("results").get(0).path("id").asInt());
		assertEquals(1, data(service.searchThreads("jmc-dev", "2026-02", "alex", null, null)).path("total").asInt());
		assertEquals(0, data(service.searchThreads("jmc-dev", "2026-02", "nothing here", null, null)).path("total").asInt());
	}

	@Test
	void defaultsToTheNewestArchivedMonthAndValidatesArguments() {
		JsonNode data = data(service.searchThreads("jmc-dev", null, null, null, null));
		assertEquals(List.of("/pipermail/jmc-dev/", "/pipermail/jmc-dev/2026-February/thread.html"), requests);
		assertEquals("2026-February", data.path("month").asText());
		assertEquals(2, data.path("availableMonths").size());
		assertEquals("2026-January", data.path("availableMonths").get(1).asText());
		assertTrue(data.path("archiveIndex").asText().endsWith("/pipermail/jmc-dev/"));
		requests.clear();
		index = "<html><body>No archives</body></html>";
		assertEquals(502, service.searchThreads("jdk-dev", null, null, null, null).getStatus());
		requests.clear();
		assertEquals(400, service.searchThreads("../etc", "2026-02", null, null, null).getStatus());
		assertEquals(400, service.searchThreads("jmc-dev", "February", null, null, null).getStatus());
		assertEquals(400, service.searchThreads("jmc-dev", "2026-13", null, null, null).getStatus());
		assertEquals(400, service.searchThreads("jmc-dev", "2026-02", "a\nb", null, null).getStatus());
		assertEquals(400, service.searchThreads("jmc-dev", "2026-02", null, 0, null).getStatus());
		assertEquals(0, requests.size());
		assertEquals(404, assertThrows(UpstreamException.class, () -> service.searchThreads("jmc-dev", "2025-01", null, null, null)).status());
	}

	@Test
	void unexpectedPagesAreReported() {
		page = "<html><body><p>Maintenance</p></body></html>";
		assertEquals(502, service.searchThreads("jmc-dev", "2026-02", null, null, null).getStatus());
	}

	private JsonNode data(Response response) {
		return json.valueToTree(response.getEntity());
	}

	private static String fixture(String name) throws IOException {
		try (var input = MailingListServiceTest.class.getResourceAsStream(name)) {
			if (input == null) throw new IOException("Missing fixture");
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
