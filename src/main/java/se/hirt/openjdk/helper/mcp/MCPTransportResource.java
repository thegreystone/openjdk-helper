/*
 * Copyright (C) 2024-2026 Marcus Hirt
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
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Human-readable help lives outside the MCP protocol endpoint. */
@Path("/mcp-info")
@ApplicationScoped
public class MCPTransportResource {
	@GET
	@Produces(MediaType.TEXT_HTML)
	public String mcpInfo() {
		return """
				<!DOCTYPE html>
				<html lang="en">
				<head><meta charset="utf-8"><title>OpenJDK Helper MCP Server</title></head>
				<body>
				<h1>OpenJDK Helper MCP Server</h1>
				<p>OpenJDK census, GitHub reviews and backports, JBS issues, JEPs, contributor guidance and project processes are available through MCP and REST.</p>
				<ul>
				<li><code>mcp</code>: Streamable HTTP endpoint. Configure your MCP client with this URL.</li>
				<li><code>mcp/sse</code>: legacy HTTP/SSE transport.</li>
				<li><a href="openapi">OpenAPI schema</a> for REST clients and Custom GPT actions.</li>
				<li><a href="processes">Project process summaries</a> with official links.</li>
				</ul>
				<p>For a local stdio client, launch the jar or native executable with <code>-Dquarkus.profile=stdio</code>.</p>
				</body>
				</html>
				""";
	}
}
