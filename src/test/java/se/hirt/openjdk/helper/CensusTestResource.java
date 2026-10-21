/* Copyright (C) 2026 Marcus Hirt */
package se.hirt.openjdk.helper;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Serves the committed census fixture to JVM, stdio and native tests. */
public class CensusTestResource implements QuarkusTestResourceLifecycleManager, AutoCloseable {
	private HttpServer server;

	@Override
	public Map<String, String> start() {
		try (InputStream input = CensusTestResource.class.getResourceAsStream("/census-small.xml")) {
			if (input == null) {
				throw new IOException("Missing census-small.xml fixture");
			}
			byte[] html = input.readAllBytes();
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/census", exchange -> {
				exchange.getResponseHeaders().set("Content-Type", "application/xml; charset=UTF-8");
				exchange.sendResponseHeaders(200, html.length);
				try (var output = exchange.getResponseBody()) {
					output.write(html);
				}
			});
			server.createContext("/", exchange -> {
				String path = exchange.getRequestURI().getPath();
				String query = exchange.getRequestURI().getQuery();
				String body = fixture(path, query == null ? "" : query);
				int status = body == null ? 404 : path.endsWith("JMC-403") ? 403 : 200;
				byte[] bytes = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(status, bytes.length);
				try (var output = exchange.getResponseBody()) { output.write(bytes); }
			});
			server.start();
			String base = "http://127.0.0.1:" + server.getAddress().getPort();
			Map<String, String> config = new LinkedHashMap<>();
			config.put("openjdk.census.url", base + "/census");
			config.put("openjdk.github.api-base-url", base + "/github");
			config.put("openjdk.github.raw-base-url", base + "/raw");
			config.put("openjdk.jbs.base-url", base + "/jbs");
			config.put("openjdk.jeps.base-url", base);
			config.put("openjdk.site.base-url", base);
			config.put("openjdk.mail.base-url", base);
			// Tests must not send ambient personal credentials to fixture endpoints.
			config.put("openjdk.github.token", "");
			config.put("openjdk.jbs.token", "");
			return config;
		} catch (IOException e) {
			throw new IllegalStateException("Could not start census fixture server", e);
		}
	}

	private static final String SHA = "a".repeat(40);
	private static final String PULL = """
			{"number":42,"title":"JMC-8639: Fixture change","state":"open","draft":false,
			 "user":{"login":"hirt"},"head":{"sha":"%s","ref":"fix"},"base":{"ref":"master"},
			 "body":"Description\\n<!-- Skara -->\\n### Progress\\n- [ ] Change requires a review\\n### Issue\\nJMC-8639\\n### Reviewers\\n * [Alex Macdonald](https://openjdk.org/census#aptmac) (@aptmac - **Reviewer**)",
			 "html_url":"https://github.com/openjdk/jmc/pull/42","labels":[{"name":"rfr"}],
			 "created_at":"2026-09-01T00:00:00Z","updated_at":"2026-09-02T00:00:00Z"}
			""".formatted(SHA);
	private static final String INTEGRATED = """
			{"number":41,"title":"JMC-8600: Earlier change","state":"closed","user":{"login":"hirt"},
			 "html_url":"https://github.com/openjdk/jmc/pull/41","labels":[{"name":"integrated"}],
			 "created_at":"2026-08-01T00:00:00Z","closed_at":"2026-08-03T00:00:00Z"}
			""";
	private static final String ISSUE = """
			{"key":"JMC-8639","fields":{"summary":"Fixture issue","status":{"name":"Open"},
			"issuetype":{"name":"Bug"},"fixVersions":[{"name":"10"}],"issuelinks":[]}}
			""";
	private static final String BACKPORT = """
			{"key":"JMC-8640","fields":{"summary":"Fixture issue","status":{"name":"Resolved"},"resolution":{"name":"Fixed"},
			"issuetype":{"name":"Backport"},"fixVersions":[{"name":"9.1.1"}],"issuelinks":[]}}
			""";

	/** Small schema-faithful payloads exercise serialization and parsing in native images too. */
	private static String fixture(String path, String query) {
		if (path.equals("/github/users/openjdk/repos")) return "[{\"name\":\"jmc\",\"full_name\":\"openjdk/jmc\",\"default_branch\":\"master\"}]";
		if (path.equals("/github/repos/openjdk/jmc/pulls")) return "[" + PULL + "]";
		if (path.equals("/github/repos/openjdk/jmc/pulls/42")) return PULL;
		if (path.equals("/github/search/issues")) {
			boolean empty = query.contains("closed:%3C") || query.contains("closed:<") || query.contains("is:open") || query.contains("-label:integrated");
			return empty ? "{\"total_count\":0,\"incomplete_results\":false,\"items\":[]}"
					: "{\"total_count\":1,\"incomplete_results\":false,\"items\":[" + INTEGRATED + "]}";
		}
		if (path.equals("/jbs/rest/api/2/search") && query.contains("linkedIssues")) return "{\"total\":1,\"issues\":[" + BACKPORT + "]}";
		if (path.equals("/projects/jdk/26/")) return resource("/releases/jdk26.html");
		if (path.equals("/pipermail/jmc-dev/2026-February/thread.html")) return resource("/mail/thread.html");
		if (path.equals("/guide/")) return resource("/guide/index.html");
		if (path.endsWith("/pulls/42/reviews")) return "[{\"id\":1,\"state\":\"APPROVED\",\"commit_id\":\"" + SHA + "\",\"user\":{\"login\":\"aptmac\"}}]";
		if (path.endsWith("/check-runs")) return "{\"total_count\":1,\"check_runs\":[{\"name\":\"build\",\"status\":\"completed\",\"conclusion\":\"success\",\"head_sha\":\"" + SHA + "\"}]}";
		if (path.endsWith("/status")) return "{\"state\":\"success\",\"total_count\":0,\"statuses\":[]}";
		if (path.equals("/github/repos/openjdk/jmc/commits/" + SHA)) return "{\"sha\":\"" + SHA + "\",\"commit\":{\"message\":\"JMC-8639: Fixture change\"}}";
		if (path.endsWith("/branches/master")) return "{\"commit\":{\"sha\":\"" + SHA + "\"}}";
		if (path.contains("/compare/")) return "{\"status\":\"identical\"}";
		if (path.equals("/jbs/rest/api/2/issue/JMC-8639")) return ISSUE;
		if (path.equals("/jbs/rest/api/2/issue/JMC-403")) return "{}";
		if (path.equals("/jbs/rest/api/2/search")) return "{\"total\":1,\"issues\":[" + ISSUE + "]}";
		if (path.equals("/raw/openjdk/jdk17u/master/.jcheck/conf")) return """
				[general]
				project=jdk-updates
				jbs=JDK
				[checks "reviewers"]
				reviewers=1
				[checks "problemlists"]
				dirs=test/jdk
				""";
		if (path.equals("/raw/openjdk/jdk17u/master/doc/building.md")) return "# Building\nConfigure a Boot JDK before building.\n";
		if (path.equals("/raw/openjdk/jdk17u/master/doc/testing.md")) return "# Testing\nUse jtreg for the regression suite.\n";
		if (path.equals("/raw/openjdk/jdk17u/master/test/jdk/ProblemList.txt")) return "java/lang/Example.java 1234567 generic-all\n";
		if (path.equals("/jeps/517")) return """
				<h1>JEP 517: Fixture feature</h1><table class="head">
				<tr><td>Type</td><td>Feature</td></tr><tr><td>Status</td><td>Targeted</td></tr>
				<tr><td>Release</td><td>26</td></tr></table>
				<h2 id="Summary">Summary</h2><p>Fixture feature summary.</p><h2>Goals</h2>
				""";
		if (path.equals("/jeps/0")) return """
				<table class="jeps"><tr><td title="Type: Feature">F</td><td title="Status: Targeted">T</td>
				<td title="Release: 26">26</td><td class="jep">517</td><td><a href="517">Fixture feature</a></td></tr></table>
				""";
		return null;
	}

	private static String resource(String name) {
		try (InputStream input = CensusTestResource.class.getResourceAsStream(name)) {
			if (input == null) throw new IllegalStateException("Missing fixture " + name);
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void stop() {
		if (server != null) {
			server.stop(0);
			server = null;
		}
	}

	@Override
	public void close() {
		stop();
	}
}
