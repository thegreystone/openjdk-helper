/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Bounded public-data fetches shared by the remote and local transports. */
@ApplicationScoped
public class UpstreamHttp implements AutoCloseable {
	@ConfigProperty(name = "openjdk.upstream.timeout-seconds", defaultValue = "20")
	int timeoutSeconds = 20;
	@ConfigProperty(name = "openjdk.upstream.cache-seconds", defaultValue = "120")
	int cacheSeconds = 120;
	private static final int MAX_BYTES = 2 * 1024 * 1024;
	private static final int MAX_ENTRIES = 128;
	private static final long MAX_CACHE_BYTES = 16 * 1024 * 1024;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NEVER).build();
	private final ObjectMapper json = new ObjectMapper();
	private final Map<String, FetchResult> cache = new LinkedHashMap<>(16, .75f, true);
	private long cacheBytes;

	public record FetchResult(String body, int status, HttpHeaders headers, Instant fetchedAt, boolean cached) {
	}

	public FetchResult get(URI uri, String token) {
		String key = uri + "#" + fingerprint(token == null ? "" : token);
		Instant now = Instant.now();
		synchronized (cache) {
			FetchResult previous = cache.get(key);
			if (previous != null && previous.fetchedAt().plusSeconds(Math.max(0, cacheSeconds)).isAfter(now)) {
				return new FetchResult(previous.body(), previous.status(), previous.headers(), previous.fetchedAt(),
						true);
			}
		}
		HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET()
				.timeout(Duration.ofSeconds(Math.max(1, Math.min(60, timeoutSeconds))))
				.header("Accept", "application/json, text/html, text/plain, application/xml")
				.header("User-Agent", "OpenJDK-Helper/0.2 (+https://github.com/thegreystone/openjdk-helper)");
		if (token != null && !token.isBlank())
			request.header("Authorization", "Bearer " + token.trim());
		CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request.build(), info -> new BoundedBody());
		try {
			HttpResponse<byte[]> response = pending.get(Math.max(1, Math.min(60, timeoutSeconds)), TimeUnit.SECONDS);
			int status = response.statusCode();
			if (status < 200 || status >= 300) {
				Map<String, String> hints = new LinkedHashMap<>();
				response.headers().firstValue("retry-after").ifPresent(v -> hints.put("retryAfter", v));
				response.headers().firstValue("x-ratelimit-reset").ifPresent(v -> hints.put("rateLimitReset", v));
				response.headers().firstValue("x-ratelimit-remaining")
						.ifPresent(v -> hints.put("rateLimitRemaining", v));
				// Redirects are never followed (tokens must not leak across hosts); report where the resource moved.
				if (status >= 300 && status < 400)
					response.headers().firstValue("location").ifPresent(v -> hints.put("location", v));
				String detail = detail(response.body());
				if (!detail.isEmpty())
					hints.put("detail", detail);
				if (status == 403 && ("0".equals(hints.get("rateLimitRemaining")) || hints.containsKey("retryAfter")))
					status = 429;
				throw new UpstreamException(uri, status, response.statusCode(),
						"Upstream returned HTTP " + response.statusCode(), hints);
			}
			byte[] bytes = response.body();
			FetchResult result = new FetchResult(new String(bytes, StandardCharsets.UTF_8), status, response.headers(),
					Instant.now(), false);
			synchronized (cache) {
				FetchResult previous = cache.remove(key);
				if (previous != null)
					cacheBytes -= previous.body().length() * 2L;
				cache.put(key, result);
				cacheBytes += result.body().length() * 2L;
				while (cache.size() > MAX_ENTRIES || cacheBytes > MAX_CACHE_BYTES) {
					var iterator = cache.entrySet().iterator();
					cacheBytes -= iterator.next().getValue().body().length() * 2L;
					iterator.remove();
				}
			}
			return result;
		} catch (InterruptedException e) {
			pending.cancel(true);
			Thread.currentThread().interrupt();
			throw new UpstreamException(uri, 503, "Upstream request interrupted", Map.of());
		} catch (TimeoutException e) {
			pending.cancel(true);
			throw new UpstreamException(uri, 504, "Upstream request timed out", Map.of());
		} catch (ExecutionException e) {
			if (e.getCause() instanceof java.net.http.HttpTimeoutException)
				throw new UpstreamException(uri, 504, "Upstream request timed out", Map.of());
			if (e.getCause() instanceof ResponseTooLargeException)
				throw new UpstreamException(uri, 502, "Upstream response exceeded the 2 MiB limit", Map.of());
			throw new UpstreamException(uri, 502, "Could not read upstream response", Map.of());
		}
	}

	/** Enforces the byte limit during delivery; the future deadline covers headers AND body. */
	private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
		private final CompletableFuture<byte[]> body = new CompletableFuture<>();
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		private Flow.Subscription subscription;

		@Override
		public CompletionStage<byte[]> getBody() {
			return body;
		}

		@Override
		public void onSubscribe(Flow.Subscription value) {
			subscription = value;
			value.request(1);
		}

		@Override
		public void onNext(List<ByteBuffer> buffers) {
			for (ByteBuffer buffer : buffers) {
				if ((long) bytes.size() + buffer.remaining() > MAX_BYTES) {
					subscription.cancel();
					body.completeExceptionally(new ResponseTooLargeException());
					return;
				}
				byte[] chunk = new byte[buffer.remaining()];
				buffer.get(chunk);
				bytes.writeBytes(chunk);
			}
			subscription.request(1);
		}

		@Override
		public void onError(Throwable error) {
			body.completeExceptionally(error);
		}

		@Override
		public void onComplete() {
			body.complete(bytes.toByteArray());
		}
	}

	private static final class ResponseTooLargeException extends IOException {
	}

	public JsonNode getJson(URI uri, String token) {
		try {
			JsonNode result = json.readTree(get(uri, token).body());
			if (result == null)
				throw new IOException("Empty response");
			return result;
		} catch (IOException e) {
			throw new UpstreamException(uri, 502, "Upstream did not return valid JSON", Map.of());
		}
	}

	/**
	 * A short, bounded upstream explanation (GitHub and Jira send a JSON "message"); never a token.
	 */
	private String detail(byte[] body) {
		if (body == null || body.length == 0)
			return "";
		String text = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.UTF_8).strip();
		try {
			JsonNode node = json.readTree(text);
			if (node != null && node.path("message").isTextual())
				text = node.path("message").asText();
			else if (node != null && node.path("errorMessages").isArray() && node.path("errorMessages").size() > 0)
				text = node.path("errorMessages").get(0).asText();
			else if (node != null && node.isObject())
				return "";
		} catch (IOException e) {
			if (text.startsWith("<"))
				return "";
		}
		text = text.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
		return text.length() > 300 ? text.substring(0, 300) : text;
	}

	private static String fingerprint(String token) {
		try {
			return HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	@PreDestroy
	public void close() {
		client.close();
	}
}
