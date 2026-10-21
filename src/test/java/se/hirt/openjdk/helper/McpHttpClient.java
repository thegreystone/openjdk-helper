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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

final class McpHttpClient implements McpTestClient {
	private final URI endpoint;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private String sessionId;
	private String protocolVersion;
	private int nextId;

	McpHttpClient(URI endpoint) {
		this.endpoint = endpoint;
	}

	@Override
	public JsonNode request(String method, JsonNode params) throws Exception {
		int id = nextId++;
		ObjectNode message = JSON.createObjectNode().put("jsonrpc", "2.0").put("id", id).put("method", method);
		message.set("params", params == null ? JSON.createObjectNode() : params);
		HttpResponse<String> response = post(message);
		assertEquals(200, response.statusCode(), response.body());
		assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("application/json"),
				response.body());
		JsonNode body = JSON.readTree(response.body());
		assertEquals("2.0", body.path("jsonrpc").asText());
		assertEquals(id, body.path("id").asInt(-1));
		if (method.equals("initialize")) {
			sessionId = response.headers().firstValue("Mcp-Session-Id").orElseThrow();
			protocolVersion = body.path("result").path("protocolVersion").asText();
			assertFalse(sessionId.isBlank());
		}
		return body;
	}

	@Override
	public void notify(String method) throws Exception {
		HttpResponse<String> response = post(JSON.createObjectNode().put("jsonrpc", "2.0").put("method", method));
		assertEquals(202, response.statusCode(), response.body());
	}

	private HttpResponse<String> post(ObjectNode message) throws Exception {
		return client.send(
				headers().POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(message))).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private HttpRequest.Builder headers() {
		HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
				.header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream");
		if (sessionId != null) {
			builder.header("Mcp-Session-Id", sessionId).header("MCP-Protocol-Version", protocolVersion);
		}
		return builder;
	}

	@Override
	public void close() throws Exception {
		try {
			if (sessionId != null) {
				HttpResponse<String> response = client.send(headers().DELETE().build(),
						HttpResponse.BodyHandlers.ofString());
				assertEquals(200, response.statusCode(), response.body());
			}
		} finally {
			client.close();
		}
	}
}
