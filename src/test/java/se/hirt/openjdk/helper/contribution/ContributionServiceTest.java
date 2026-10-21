/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.contribution;

import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.hirt.openjdk.helper.census.CensusService;
import se.hirt.openjdk.helper.common.UpstreamException;
import se.hirt.openjdk.helper.common.UpstreamHttp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ContributionServiceTest {
	private HttpServer server;
	private UpstreamHttp http;
	private ContributionService service;
	private final Set<String> requests = ConcurrentHashMap.newKeySet();

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getRawPath();
			requests.add(path);
			String text = fixture(path);
			if (text == null) {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}
			byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
			exchange.sendResponseHeaders(200, bytes.length);
			try (var output = exchange.getResponseBody()) {
				output.write(bytes);
			}
		});
		server.start();
		http = new UpstreamHttp();
		service = new ContributionService();
		service.http = http;
		service.rawBaseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
		service.census = new CensusService() {
			@Override
			public Response getProjectMembers(
				String project, String roles, String query, Integer limit, Integer offset) {
				assertEquals("lead,reviewer,committer", roles);
				assertEquals(30, limit);
				return Response.ok(
						Map.of("projectId", project, "results", List.of(Map.of("userId", "alice", "role", "Reviewer"))))
						.build();
			}
		};
	}

	@AfterEach
	void stop() {
		if (http != null)
			http.close();
		if (server != null)
			server.stop(0);
	}

	@Test
	void resolvesCensusProjectAndRulesFromRequestedRef() {
		Map<String, Object> context = service.getContributionContext("openjdk", "jdk17u", "release/17");
		assertEquals("jdk-updates", context.get("projectId"));
		assertEquals("JDK", context.get("jbsProject"));
		assertEquals("17.0.21", context.get("version"));
		assertEquals("2", object(context.get("reviewRules")).get("reviewers"));
		assertEquals("jdk-updates", object(context.get("censusMembers")).get("projectId"));
		assertTrue(requests.contains("/openjdk/jdk17u/release%2F17/.jcheck/conf"));
		assertNotNull(object(context.get("source")).get("fetchedAt"));
		assertEquals("openjfx", service.getContributionContext("openjdk", "jfx", "main").get("projectId"));
	}

	@Test
	void keepsMissingReviewRulesUnspecified() {
		Map<String, Object> context = service.getContributionContext("openjdk", "jfx", "main");
		assertEquals(Map.of(), context.get("reviewRules"));
		assertEquals("main", context.get("ref"));
	}

	@Test
	void retrievesBranchSpecificVerbatimBuildAndTestEvidence() {
		Map<String, Object> guidance = service.getBuildGuidance("openjdk", "jdk17u", "release/17", "boot-jdk",
				"ExampleTest.java");
		List<Map<String, Object>> documents = objects(guidance.get("documents"));
		assertEquals(2, documents.size());
		String excerpt = (String) objects(documents.get(0).get("excerpts")).getFirst().get("text");
		assertTrue(excerpt.contains("Use JDK 16 or 17 as the boot JDK."));
		assertTrue(excerpt.contains("bash configure --with-boot-jdk=/fixture/jdk-17"));
		assertFalse(excerpt.contains("JDK 25"));
		assertEquals(false, documents.get(0).get("completeDocument"));
		Map<String, Object> lists = object(guidance.get("problemLists"));
		List<Map<String, Object>> files = objects(lists.get("files"));
		assertEquals(1, files.size());
		Map<String, Object> match = objects(files.getFirst().get("results")).getFirst();
		assertEquals("java/lang/ExampleTest.java", match.get("test"));
		assertEquals("linux-all", match.get("platforms"));
		assertTrue(requests.contains("/openjdk/jdk17u/release%2F17/test/jdk/ProblemList.txt"));
	}

	@Test
	void missingDocsAreExplicitAndMissingConfigurationFails() {
		Map<String, Object> guidance = service.getBuildGuidance("openjdk", "missing", "master", null, null);
		for (Map<String, Object> document : objects(guidance.get("documents"))) {
			assertEquals(false, document.get("available"));
			assertEquals(404, document.get("status"));
		}
		assertEquals(404, assertThrows(UpstreamException.class,
				() -> service.getContributionContext("openjdk", "missing", "master")).status());
	}

	@Test
	void rejectsPathTraversalAndOversizedQueryBeforeFetching() {
		assertThrows(WebApplicationException.class, () -> service.getContributionContext("openjdk", "../jdk", "main"));
		assertThrows(WebApplicationException.class, () -> service.getContributionContext("openjdk", "jdk", "../main"));
		assertThrows(WebApplicationException.class,
				() -> service.getBuildGuidance("openjdk", "jdk", "main", "x".repeat(201), null));
		assertTrue(requests.isEmpty());
	}

	@Test
	void excerptsStayBoundedAndPreserveExactSourceText() {
		String document = ("## jtreg section\n" + "Read documented command. ".repeat(120) + "\n").repeat(100);
		List<Map<String, Object>> excerpts = ContributionService.excerpts(document, "jtreg");
		assertTrue(excerpts.size() <= 3);
		assertTrue(excerpts.stream().mapToInt(item -> ((String) item.get("text")).length()).sum() <= 6000);
		for (Map<String, Object> excerpt : excerpts)
			assertTrue(document.contains((String) excerpt.get("text")));
		assertEquals(List.of(), ContributionService.excerpts(document, "unmatched-topic"));
	}

	@Test
	void cachedSourcesRetainProvenance() {
		service.getContributionContext("openjdk", "jfx", "main");
		Map<String, Object> second = service.getContributionContext("openjdk", "jfx", "main");
		assertEquals(true, object(second.get("source")).get("cached"));
	}

	private static String fixture(String path) {
		if (path.startsWith("/openjdk/missing/"))
			return null;
		if (path.endsWith("/.jcheck/conf")) {
			if (path.contains("/jfx/"))
				return "[general]\nproject=openjfx\njbs=JDK\n";
			return """
					[general]
					project=jdk-updates
					jbs=JDK
					version=17.0.21
					[checks]
					error=author,committer,reviewers
					[checks "reviewers"]
					reviewers=2
					ignore=duke
					[checks "committer"]
					role=committer
					[checks "problemlists"]
					dirs=test/jdk
					""";
		}
		if (path.endsWith("/doc/building.md"))
			return "% Building\n\n## Boot JDK requirements\nUse JDK 16 or 17 as the boot JDK.\n\n    bash configure --with-boot-jdk=/fixture/jdk-17\n";
		if (path.endsWith("/doc/testing.md"))
			return "% Testing\n\n## jtreg\n    make test TEST=java/lang/ExampleTest.java\n";
		if (path.endsWith("/test/jdk/ProblemList.txt"))
			return "# Fixture\njava/lang/ExampleTest.java 1234567 linux-all\njava/lang/OtherTest.java 7654321 generic-all\n";
		return null;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> object(Object value) {
		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> objects(Object value) {
		return (List<Map<String, Object>>) value;
	}
}
