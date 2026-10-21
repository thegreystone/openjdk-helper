/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.guide;

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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DevelopersGuideServiceTest {
	private final ObjectMapper json = new ObjectMapper();
	private final AtomicInteger calls = new AtomicInteger();
	private HttpServer server;
	private UpstreamHttp http;
	private DevelopersGuideService service;
	private volatile String page;

	@BeforeEach
	void start() throws IOException {
		page = fixture();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/guide/", exchange -> {
			calls.incrementAndGet();
			byte[] bytes = page.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
			exchange.sendResponseHeaders(200, bytes.length);
			try (var out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
		http = new UpstreamHttp();
		service = new DevelopersGuideService(http, "http://127.0.0.1:" + server.getAddress().getPort());
	}

	@AfterEach
	void stop() {
		if (http != null)
			http.close();
		if (server != null)
			server.stop(0);
	}

	@Test
	void returnsTitleMatchesBeforeBodyMatchesWithVerbatimExcerpts() {
		JsonNode data = data(service.search("backport", null));
		assertEquals("available", data.path("status").asText());
		assertEquals(1, data.path("total").asInt());
		JsonNode section = data.path("results").get(0);
		assertEquals("backporting", section.path("id").asText());
		assertEquals("title", section.path("matchedOn").asText());
		assertEquals(2, section.path("level").asInt());
		assertEquals("Working With Pull Requests", section.path("breadcrumb").get(0).asText());
		assertTrue(section.path("url").asText().endsWith("/guide/#backporting"));
		assertTrue(section.path("excerpt").asText()
				.startsWith("Backporting a fix means applying it to an older release."));
		assertFalse(section.path("truncated").asBoolean());

		JsonNode body = data(service.search("eight significant", 1));
		assertEquals("becoming-a-committer", body.path("results").get(0).path("id").asText());
		assertEquals("text", body.path("results").get(0).path("matchedOn").asText());
		assertEquals(2, body.path("results").get(0).path("breadcrumb").size());
	}

	@Test
	void emptyQueryListsTheTableOfContentsAndTheDocumentIsCached() {
		JsonNode toc = data(service.search(null, null));
		assertEquals(5, toc.path("sections").size(),
				"Only first and second level headings with ids in the table of contents");
		assertEquals(6, toc.path("total").asInt());
		assertEquals("introduction", toc.path("sections").get(0).path("id").asText());
		data(service.search("pull request", 20));
		assertEquals(1, calls.get());
	}

	@Test
	void validatesArgumentsAndUnexpectedPages() {
		assertEquals(400, service.search("x".repeat(201), null).getStatus());
		assertEquals(400, service.search("ok", 21).getStatus());
		assertEquals(0, calls.get());
		page = "<html><body><p>Nothing</p></body></html>";
		assertEquals(502, service.search("ok", null).getStatus());
	}

	private JsonNode data(Response response) {
		return json.valueToTree(response.getEntity());
	}

	private static String fixture() throws IOException {
		try (var input = DevelopersGuideServiceTest.class.getResourceAsStream("/guide/index.html")) {
			if (input == null)
				throw new IOException("Missing fixture");
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
