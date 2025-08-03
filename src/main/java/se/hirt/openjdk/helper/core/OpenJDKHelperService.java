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
package se.hirt.openjdk.helper.core;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import se.hirt.openjdk.helper.census.CensusService;
import se.hirt.openjdk.helper.github.GitHubService;

/**
 * Core service that combines all functionality of the OpenJDK Helper. This is the central service that can be used by all interfaces:
 * - REST/OpenAPI endpoints
 * - HTTP MCP endpoints
 * - Stdio MCP implementation
 */
@ApplicationScoped
public class OpenJDKHelperService {
	private static final Logger LOG = Logger.getLogger(OpenJDKHelperService.class);

	@Inject
	private CensusService censusService;

	@Inject
	private GitHubService githubService;

	@ConfigProperty(name = "quarkus.application.version", defaultValue = "unknown")
	String configVersion;

	/**
	 * Returns the census service.
	 *
	 * @return the census service
	 */
	public CensusService getCensusService() {
		return censusService;
	}

	/**
	 * Returns the GitHub service.
	 *
	 * @return the GitHub service
	 */
	public GitHubService getGitHubService() {
		return githubService;
	}

	/**
	 * Returns the version of the service.
	 *
	 * @return the version string
	 */
	public String getVersion() {
		return configVersion;
	}
}
