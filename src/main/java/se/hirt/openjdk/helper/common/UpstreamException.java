/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.common;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

public class UpstreamException extends RuntimeException {
    private final int status;
    private final int upstreamStatus;
    private final URI source;
    private final Map<String, String> hints;

    public UpstreamException(URI source, int status, String message, Map<String, String> hints) {
        this(source, status, status, message, hints);
    }

    public UpstreamException(URI source, int status, int upstreamStatus, String message, Map<String, String> hints) {
        super(message);
        this.source = source;
        this.status = status;
        this.upstreamStatus = upstreamStatus;
        this.hints = Map.copyOf(hints);
    }

    public int status() { return status; }
    public int upstreamStatus() { return upstreamStatus; }
    public int httpStatus() { return status == 404 || status == 422 || status == 429 || status == 504 ? status : 502; }
    public Map<String, Object> error() {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", status == 429 ? "upstream_rate_limited" : status == 403 || status == 401
                ? "upstream_access_denied" : status == 404 ? "not_found"
                : status == 422 ? "upstream_rejected_request"
                : status >= 300 && status < 400 ? "upstream_moved" : "upstream_unavailable");
        error.put("message", getMessage());
        error.put("upstreamStatus", upstreamStatus);
        error.put("source", source.toString());
        error.putAll(hints);
        return error;
    }
}
