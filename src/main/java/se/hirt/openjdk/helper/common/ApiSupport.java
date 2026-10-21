/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.common;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

public final class ApiSupport {
	private ApiSupport() {
	}

	public static WebApplicationException badRequest(String message) {
		return new WebApplicationException(
				Response.status(400).entity(Map.of("error", "invalid_argument", "message", message)).build());
	}

	public static String requireName(String value, String label) {
		if (value == null || !value.matches("[A-Za-z0-9_.-]{1,100}") || value.equals(".") || value.equals("..")) {
			throw badRequest(label + " must contain only letters, numbers, underscores, dots or hyphens");
		}
		return value;
	}

	public static int limit(Integer value) {
		int limit = value == null ? 30 : value;
		if (limit < 1 || limit > 100)
			throw badRequest("limit must be between 1 and 100");
		return limit;
	}

	public static int offset(Integer value) {
		int offset = value == null ? 0 : value;
		if (offset < 0 || offset > 10000)
			throw badRequest("offset must be between 0 and 10000");
		return offset;
	}

	public static String segment(String value) {
		if (value == null || value.isBlank())
			throw badRequest("Path value is required");
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	public static URI uri(String base, String path, Map<String, ?> query) {
		URI origin = URI.create(base);
		if (!("https".equals(origin.getScheme()) || "http".equals(origin.getScheme())) || origin.getHost() == null
				|| origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null) {
			throw new IllegalArgumentException(
					"Upstream base URL must be an HTTP(S) URL without credentials, query or fragment");
		}
		String url = base.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
		if (query != null && !query.isEmpty()) {
			String parameters = query.entrySet().stream().filter(e -> e.getValue() != null).map(
					e -> segment(e.getKey()) + "=" + URLEncoder.encode(e.getValue().toString(), StandardCharsets.UTF_8))
					.collect(Collectors.joining("&"));
			if (!parameters.isEmpty())
				url += "?" + parameters;
		}
		return URI.create(url);
	}
}
