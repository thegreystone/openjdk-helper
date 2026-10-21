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

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.net.URI;

@QuarkusTest
@QuarkusTestResource(CensusTestResource.class)
class McpHttpTest {
	@TestHTTPResource("/mcp")
	URI endpoint;

	@Test
	void streamableHttpExposesAndExecutesTheTools() throws Exception {
		try (var client = new McpHttpClient(endpoint)) {
			McpProtocolAssertions.verify(client);
		}
	}

	/**
	 * Replicas behind a load balancer share no session store, so requests with a foreign session
	 * must still work.
	 */
	@Test
	void requestsWithUnknownOrMissingSessionsAreServedStatelessly() throws Exception {
		try (var http = java.net.http.HttpClient.newHttpClient()) {
			String call = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"getVersion\",\"arguments\":{}}}";
			for (String[] headers : new String[][] {{"Mcp-Session-Id", "c2Vzc2lvbi1mcm9tLWFub3RoZXItcG9k"},
					{"Accept", "application/json"}}) {
				var request = java.net.http.HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json")
						.header("Accept", "application/json, text/event-stream")
						.header("MCP-Protocol-Version", McpTestClient.PROTOCOL_VERSION).header(headers[0], headers[1])
						.POST(java.net.http.HttpRequest.BodyPublishers.ofString(call)).build();
				var response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
				org.junit.jupiter.api.Assertions.assertEquals(200, response.statusCode(), response.body());
				var json = McpTestClient.JSON.readTree(response.body());
				org.junit.jupiter.api.Assertions.assertFalse(json.has("error"), response.body());
				org.junit.jupiter.api.Assertions.assertFalse(json.path("result").path("isError").asBoolean(),
						response.body());
				org.junit.jupiter.api.Assertions
						.assertFalse(json.path("result").path("content").get(0).path("text").asText().isBlank());
			}
		}
	}
}
