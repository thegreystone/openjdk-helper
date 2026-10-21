/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.releases;

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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseServiceTest {
	private final ObjectMapper json = new ObjectMapper();
	private HttpServer server;
	private UpstreamHttp http;
	private volatile String page;
	private volatile int status = 200;
	private String base;

	@BeforeEach
	void start() throws IOException {
		page = fixture();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/projects/jdk/", exchange -> {
			byte[] bytes = page.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
			exchange.sendResponseHeaders(status, bytes.length);
			try (var out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
		http = new UpstreamHttp();
	}

	@AfterEach
	void stop() {
		if (http != null)
			http.close();
		if (server != null)
			server.stop(0);
	}

	private ReleaseService service(String today) {
		return new ReleaseService(http, base, Clock.fixed(Instant.parse(today + "T12:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void derivesPhaseFromListedMilestonesAndToday() {
		JsonNode data = data(service("2026-01-20").getReleaseSchedule(26));
		assertEquals("available", data.path("status").asText());
		assertEquals(26, data.path("release").asInt());
		assertEquals("Rampdown Phase Two", data.path("currentPhase").asText());
		assertEquals("Initial Release Candidate", data.path("nextMilestone").path("name").asText());
		assertEquals("2026-02-05", data.path("nextMilestone").path("date").asText());
		assertEquals("2026-03-17", data.path("generalAvailability").asText());
		assertFalse(data.path("shipped").asBoolean());
		assertEquals(5, data.path("milestones").size());
		assertTrue(data.path("milestones").get(0).path("reached").asBoolean());
		assertTrue(data.path("milestones").get(0).path("url").asText().endsWith("/jeps/3#rdp-1"));
		assertEquals("Rampdown Phase One (branch from main line)",
				data.path("milestones").get(0).path("name").asText());
		assertEquals(2, data.path("features").size());
		assertEquals(517, data.path("features").get(1).path("number").asInt());
		assertEquals("2026/3/16 21:53 UTC", data.path("pageLastUpdate").asText());
		assertTrue(data.path("source").asText().endsWith("/projects/jdk/26/"));
	}

	@Test
	void beforeAndAfterTheScheduleAreExplicit() {
		assertEquals("Before Rampdown Phase One",
				data(service("2025-11-01").getReleaseSchedule(26)).path("currentPhase").asText());
		JsonNode shipped = data(service("2026-04-01").getReleaseSchedule(26));
		assertEquals("General Availability", shipped.path("currentPhase").asText());
		assertTrue(shipped.path("shipped").asBoolean());
		assertTrue(shipped.path("nextMilestone").isNull());
	}

	@Test
	void rejectsInvalidReleasesAndUnexpectedPages() {
		assertEquals(400, service("2026-01-20").getReleaseSchedule(8).getStatus());
		assertEquals(400, service("2026-01-20").getReleaseSchedule(100).getStatus());
		assertEquals(502, service("2026-01-20").getReleaseSchedule(27).getStatus(),
				"Heading for another release must not be accepted");
		page = "<html><body><h1>JDK 26</h1><p>No schedule yet</p></body></html>";
		assertEquals(502, service("2026-01-20").getReleaseSchedule(26).getStatus());
		status = 404;
		// A release whose page was not cached by the calls above.
		assertEquals(404,
				assertThrows(UpstreamException.class, () -> service("2026-01-20").getReleaseSchedule(25)).status());
	}

	private JsonNode data(Response response) {
		return json.valueToTree(response.getEntity());
	}

	private static String fixture() throws IOException {
		try (var input = ReleaseServiceTest.class.getResourceAsStream("/releases/jdk26.html")) {
			if (input == null)
				throw new IOException("Missing fixture");
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
