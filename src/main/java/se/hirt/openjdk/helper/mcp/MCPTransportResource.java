/*
 * Copyright (C) 2025 Marcus Hirt
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
package se.hirt.openjdk.helper.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

/**
 * MCP helper endpoints and redirects.
 */
@Path("/mcp")
@ApplicationScoped
public class MCPTransportResource {

	private static final Logger LOG = Logger.getLogger(MCPTransportResource.class);

	/**
	 * Root MCP endpoint that provides information and redirects.
	 */
	@GET
	@Produces(MediaType.TEXT_HTML)
	public Response mcpRoot() {
		String html = "<!DOCTYPE html><html><head><title>OpenJDK Helper MCP Server</title></head><body>" + "<h1>OpenJDK Helper MCP Server</h1>" + "<p>This is the MCP Server for the OpenJDK Helper application.</p>" + "<h2>Available MCP Endpoints</h2>" + "<ul>" + "<li><a href=\"/mcp/sse\">/mcp/sse</a> - Server-Sent Events (SSE) endpoint</li>" + "</ul>" + "<h2>Connection Instructions</h2>" + "<p>To connect with the MCP Inspector, use:</p>" + "<pre>npx @modelcontextprotocol/inspector http://localhost:8080/mcp --transport-type=sse</pre>" + "</body></html>";

		return Response.ok(html).header("Content-Type", "text/html").build();
	}

	/**
	 * Handle requests to /mcp directly (for CORS and OPTIONS).
	 */
	@OPTIONS
	public Response mcpOptions() {
		return Response.ok().header("Access-Control-Allow-Origin", "*").header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
				.header("Access-Control-Allow-Headers", "Content-Type").build();
	}
}
