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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/** Small wire-level client shared by the HTTP, JVM and native smoke tests. */
interface McpTestClient extends AutoCloseable {

	ObjectMapper JSON = new ObjectMapper();
	String PROTOCOL_VERSION = "2025-11-25";

	JsonNode request(String method, JsonNode params) throws Exception;

	void notify(String method) throws Exception;

	default JsonNode initialize() throws Exception {
		ObjectNode params = JSON.createObjectNode().put("protocolVersion", PROTOCOL_VERSION);
		params.set("capabilities", JSON.createObjectNode());
		params.set("clientInfo", JSON.createObjectNode().put("name", "openjdk-helper-test").put("version", "1"));
		JsonNode response = request("initialize", params);
		notify("notifications/initialized");
		return response;
	}

	default JsonNode call(String name, Map<String, Object> arguments) throws Exception {
		ObjectNode params = JSON.createObjectNode().put("name", name);
		params.set("arguments", JSON.valueToTree(arguments));
		return request("tools/call", params);
	}
}
