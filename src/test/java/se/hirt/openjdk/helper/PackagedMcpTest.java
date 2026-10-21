/*
 * Copyright (C) 2026 Marcus Hirt
 *
 * This software is free:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESSED OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package se.hirt.openjdk.helper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** The exact same behavior is exercised in the packaged JVM and native executable. */
abstract class PackagedMcpTest {
	@TempDir
	Path temporaryDirectory;

	abstract List<String> command(List<String> properties);

	@Test
	void httpServesRestOpenApiAndMcp() throws Exception {
		try (var fixture = new CensusTestResource(); var http = HttpClient.newHttpClient()) {
			var settings = fixture.start();
			int port;
			try (var reservation = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
				port = reservation.getLocalPort();
			}
			URI base = URI.create("http://127.0.0.1:" + port);
			Path log = temporaryDirectory.resolve("server.log");
			var properties = new ArrayList<>(
					settings.entrySet().stream().map(e -> "-D" + e.getKey() + "=" + e.getValue()).toList());
			properties.addAll(List.of("-Dquarkus.http.port=" + port, "-Dquarkus.http.host=127.0.0.1"));
			Process process = new ProcessBuilder(command(properties)).redirectErrorStream(true)
					.redirectOutput(log.toFile()).start();
			try {
				awaitHttp(process, http, base.resolve("/version"), log);
				HttpResponse<String> version = get(http, base.resolve("/version"));
				assertEquals(200, version.statusCode());
				assertFalse(version.body().isBlank());
				assertFalse(version.body().contains("${"));
				HttpResponse<String> openApi = get(http, base.resolve("/openapi?format=json"));
				assertEquals(200, openApi.statusCode(), openApi.body());
				var document = McpTestClient.JSON.readTree(openApi.body());
				var paths = document.path("paths");
				assertTrue(paths.has("/version"), openApi.body());
				assertTrue(paths.has("/census/people"), openApi.body());
				assertTrue(paths.has("/github/repos"), openApi.body());
				assertTrue(paths.has("/processes"), openApi.body());
				assertTrue(paths.has("/issues/{key}"), openApi.body());
				assertTrue(paths.has("/jeps/{number}"), openApi.body());
				assertTrue(paths.has("/contribution/context"), openApi.body());
				assertTrue(paths.has("/issues/{key}/fix-status"), openApi.body());
				assertTrue(paths.has("/github/triage"), openApi.body());
				assertTrue(paths.has("/github/contributor"), openApi.body());
				assertTrue(paths.has("/github/activity"), openApi.body());
				assertTrue(paths.has("/releases/jdk/{release}"), openApi.body());
				assertTrue(paths.has("/mail/threads"), openApi.body());
				assertTrue(paths.has("/guide/search"), openApi.body());
				OpenApiAssertions.verifyImportable(document);
				HttpResponse<String> swagger = get(http, base.resolve("/swagger-ui/"));
				assertEquals(200, swagger.statusCode(), swagger.body());
				assertTrue(swagger.body().contains("SwaggerUIBundle"), swagger.body());
				HttpResponse<String> people = get(http, base.resolve("/census/people?userid=hirt"));
				assertEquals(200, people.statusCode(), people.body());
				assertEquals("hirt",
						McpTestClient.JSON.readTree(people.body()).path("results").get(0).path("userId").asText());
				try (var client = new McpHttpClient(base.resolve("/mcp"))) {
					McpProtocolAssertions.verify(client);
				}
			} finally {
				process.destroy();
				if (!process.waitFor(5, TimeUnit.SECONDS)) {
					process.destroyForcibly();
					assertTrue(process.waitFor(5, TimeUnit.SECONDS));
				}
			}
		}
	}

	@Test
	void stdioUsesCleanJsonRpcAndDoesNotBindAnHttpPort() throws Exception {
		try (var fixture = new CensusTestResource();
				var occupiedPort = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			var properties = new ArrayList<>(
					fixture.start().entrySet().stream().map(e -> "-D" + e.getKey() + "=" + e.getValue()).toList());
			properties.addAll(List.of("-Dquarkus.profile=stdio", "-Dquarkus.http.port=" + occupiedPort.getLocalPort(),
					"-Dquarkus.http.host=127.0.0.1"));
			ProcessBuilder builder = new ProcessBuilder(command(properties));
			try (var client = new McpStdioClient(builder)) {
				McpProtocolAssertions.verify(client);
			}
		}
	}

	private static HttpResponse<String> get(HttpClient http, URI uri) throws Exception {
		return http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private static void awaitHttp(Process process, HttpClient http, URI uri, Path log) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
		while (System.nanoTime() < deadline) {
			assertTrue(process.isAlive(), () -> "Server exited: " + readLog(log));
			try {
				if (get(http, uri).statusCode() == 200) {
					return;
				}
			} catch (java.io.IOException ignored) {
				// Startup has not bound the socket yet.
			}
			Thread.sleep(100);
		}
		fail("Server did not become ready: " + readLog(log));
	}

	private static String readLog(Path log) {
		try {
			return Files.readString(log);
		} catch (java.io.IOException e) {
			return e.toString();
		}
	}
}
