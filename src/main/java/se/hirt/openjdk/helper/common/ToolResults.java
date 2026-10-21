/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@ApplicationScoped
public class ToolResults {
    private static final Logger LOG = Logger.getLogger(ToolResults.class);
    @Inject ObjectMapper json;

    public ToolResponse call(Supplier<?> operation) {
        try {
            Object value = operation.get();
            if (value instanceof Response response) {
                try (response) { return encode(response.getEntity(), response.getStatus() >= 400); }
            }
            return encode(value, false);
        } catch (UpstreamException e) {
            return encode(e.error(), true);
        } catch (WebApplicationException e) {
            try (Response response = e.getResponse()) {
                return encode(response.hasEntity() ? response.getEntity() : Map.of("error", e.getMessage()), true);
            }
        } catch (Exception e) {
            // Logged to stderr so stdio and native users can diagnose; the client only gets a generic message.
            LOG.warn("Tool operation failed", e);
            return encode(Map.of("error", "operation_failed", "message", "Could not complete the requested operation"), true);
        }
    }

    private ToolResponse encode(Object value, boolean error) {
        try {
            String text = value instanceof String string ? string : json.writeValueAsString(value);
            return new ToolResponse(error, List.of(new TextContent(text)));
        } catch (Exception e) {
            return new ToolResponse(true, List.of(new TextContent("Could not serialize the response")));
        }
    }
}
