/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.common;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UpstreamHttpTest {
    @Test void cachesByCredentialAndPreservesRateLimitAdvice() throws Exception {
        var hits = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            hits.incrementAndGet();
            byte[] body = "{\"value\":42}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.createContext("/limit", exchange -> {
            exchange.getResponseHeaders().set("X-RateLimit-Remaining", "0");
            exchange.getResponseHeaders().set("Retry-After", "60");
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.createContext("/rejected", exchange -> {
            byte[] body = "{\"message\":\"No commit found for SHA: abc\",\"documentation_url\":\"https://docs.github.com\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(422, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.createContext("/moved", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://api.github.com/repositories/1");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        server.createContext("/html", exchange -> {
            byte[] body = "<html><body>Access denied by secret-token-value</body></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        try (var http = new UpstreamHttp()) {
            assertFalse(http.get(base.resolve("/ok"), null).cached());
            assertTrue(http.get(base.resolve("/ok"), null).cached());
            assertFalse(http.get(base.resolve("/ok"), "different-account").cached());
            assertEquals(2, hits.get());
            var limited = assertThrows(UpstreamException.class, () -> http.get(base.resolve("/limit"), null));
            assertEquals(429, limited.httpStatus());
            assertEquals(403, limited.error().get("upstreamStatus"));
            assertEquals("60", limited.error().get("retryAfter"));
            var rejected = assertThrows(UpstreamException.class, () -> http.get(base.resolve("/rejected"), null));
            assertEquals(422, rejected.httpStatus());
            assertEquals("upstream_rejected_request", rejected.error().get("error"));
            assertEquals("No commit found for SHA: abc", rejected.error().get("detail"));
            var moved = assertThrows(UpstreamException.class, () -> http.get(base.resolve("/moved"), null));
            assertEquals("upstream_moved", moved.error().get("error"));
            assertEquals("https://api.github.com/repositories/1", moved.error().get("location"));
            assertEquals(502, moved.httpStatus());
            var html = assertThrows(UpstreamException.class, () -> http.get(base.resolve("/html"), null));
            assertFalse(html.error().containsKey("detail"), "HTML error pages are not echoed");
        } finally { server.stop(0); }
    }

    @Test void limitsResponseSizeAndTimeoutIncludesBodyDelivery() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor(); var http = new UpstreamHttp()) {
            server.setExecutor(executor);
            server.createContext("/huge", exchange -> {
                exchange.sendResponseHeaders(200, 0);
                try (var output = exchange.getResponseBody()) { output.write(new byte[2 * 1024 * 1024 + 1]); }
                catch (java.io.IOException ignored) { /* Client cancels once the limit is reached. */ }
            });
            server.createContext("/slow", exchange -> {
                exchange.sendResponseHeaders(200, 0);
                try (var output = exchange.getResponseBody()) {
                    output.write('a'); output.flush();
                    try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    output.write('b');
                } catch (java.io.IOException ignored) { /* Client cancels on timeout. */ }
            });
            server.start();
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            assertTrue(assertThrows(UpstreamException.class, () -> http.get(base.resolve("/huge"), null))
                    .getMessage().contains("2 MiB"));
            http.timeoutSeconds = 1;
            long start = System.nanoTime();
            assertEquals(504, assertThrows(UpstreamException.class, () -> http.get(base.resolve("/slow"), null)).httpStatus());
            assertTrue(System.nanoTime() - start < java.time.Duration.ofMillis(1900).toNanos());
        } finally { server.stop(0); }
    }
}
