/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.common;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UpstreamExceptionMapper implements ExceptionMapper<UpstreamException> {
    @Override
    public Response toResponse(UpstreamException exception) {
        Response.ResponseBuilder builder = Response.status(exception.httpStatus()).entity(exception.error());
        Object retry = exception.error().get("retryAfter");
        if (retry != null) builder.header("Retry-After", retry);
        return builder.build();
    }
}
