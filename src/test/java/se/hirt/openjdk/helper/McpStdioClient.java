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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Drains both streams continuously, so logs cannot deadlock a native or JVM child process. */
final class McpStdioClient implements McpTestClient {
	private final Process process;
	private final BlockingQueue<String> stdout = new LinkedBlockingQueue<>();
	private final StringBuilder stderr = new StringBuilder();
	private final List<String> invalidStdout = new ArrayList<>();
	private final Thread stdoutReader;
	private final Thread stderrReader;
	private int nextId;

	McpStdioClient(ProcessBuilder builder) throws Exception {
		process = builder.start();
		stdoutReader = pump(process.getInputStream(), stdout::add);
		stderrReader = pump(process.getErrorStream(), line -> {
			synchronized (stderr) {
				if (stderr.length() < 64_000) {
					stderr.append(line).append('\n');
				}
			}
		});
	}

	@Override
	public JsonNode request(String method, JsonNode params) throws Exception {
		int id = nextId++;
		ObjectNode message = JSON.createObjectNode().put("jsonrpc", "2.0").put("id", id).put("method", method);
		message.set("params", params == null ? JSON.createObjectNode() : params);
		send(message);
		long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
		while (System.nanoTime() < deadline) {
			String line = stdout.poll(100, TimeUnit.MILLISECONDS);
			if (line == null) {
				assertTrue(process.isAlive(), "Server exited waiting for " + method + ":\n" + stderr());
				continue;
			}
			JsonNode response = parse(line);
			if (response != null && response.has("id")) {
				assertEquals(id, response.path("id").asInt(-1), response.toString());
				return response;
			}
		}
		return fail("Timed out waiting for " + method + ":\n" + stderr());
	}

	@Override
	public void notify(String method) throws Exception {
		send(JSON.createObjectNode().put("jsonrpc", "2.0").put("method", method));
	}

	private void send(ObjectNode message) throws Exception {
		process.getOutputStream().write((JSON.writeValueAsString(message) + "\n").getBytes(StandardCharsets.UTF_8));
		process.getOutputStream().flush();
	}

	private JsonNode parse(String line) {
		if (line.isBlank()) {
			return null;
		}
		try {
			JsonNode message = JSON.readTree(line);
			if (message != null && message.isObject() && message.path("jsonrpc").asText().equals("2.0")) {
				return message;
			}
		} catch (Exception ignored) {
			// The assertion reports every unexpected line, including banners and log output.
		}
		invalidStdout.add(line);
		return null;
	}

	private static Thread pump(InputStream input, Consumer<String> consumer) {
		return Thread.ofPlatform().daemon().start(() -> {
			try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					consumer.accept(line);
				}
			} catch (java.io.IOException ignored) {
				// Stream closure is expected when stopping the child process.
			}
		});
	}

	private String stderr() {
		synchronized (stderr) {
			return stderr.toString();
		}
	}

	@Override
	public void close() throws Exception {
		process.getOutputStream().close();
		if (!process.waitFor(5, TimeUnit.SECONDS)) {
			process.destroy();
			if (!process.waitFor(5, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Could not stop test process");
			}
		}
		stdoutReader.join(1000);
		stderrReader.join(1000);
		stdout.forEach(this::parse);
		assertTrue(invalidStdout.isEmpty(), "Only JSON-RPC belongs on stdout: " + invalidStdout);
	}
}
