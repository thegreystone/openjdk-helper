/*
 * Copyright (C) 2024-2025 Marcus Hirt
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

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * MCP Server configuration observer to ensure the MCP server is properly initialized.
 */
@ApplicationScoped
@RegisterForReflection
public class McpServerConfig {

	private static final Logger LOG = Logger.getLogger(McpServerConfig.class);

	@Inject
	OpenJDKHelperTools helperTools;

	void onStart(@Observes StartupEvent ev) {
		LOG.info("MCP Server starting up...");
		LOG.info("MCP Tools registered: " + (helperTools != null ? "Yes" : "No"));
		LOG.info("OpenJDK Helper Tools class: " + (helperTools != null ? helperTools.getClass().getName() : "Not available"));

		// Transport options
		LOG.info("MCP SSE endpoint available at: /mcp/sse");
		LOG.info("MCP stdio " + (Boolean.getBoolean("quarkus.mcp.server.stdio.enabled") ? "enabled" : "disabled"));

		// Connection instructions
		LOG.info("MCP Inspector SSE connection: npx @modelcontextprotocol/inspector http://localhost:8080/mcp --transport-type=sse");
		LOG.info("For stdio mode, restart with: java -Dquarkus.mcp.server.stdio.enabled=true -jar target/quarkus-app/quarkus-run.jar");
	}
}
