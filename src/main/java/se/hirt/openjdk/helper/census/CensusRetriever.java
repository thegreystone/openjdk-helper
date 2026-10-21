/* Copyright (C) 2024-2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.census;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ApplicationScoped
public class CensusRetriever {
	private static final String CENSUS_URL = "https://openjdk.org/census.xml";
	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private final HttpClient httpClient;
	private final URI censusUri;

	public CensusRetriever() {
		this(CENSUS_URL);
	}

	@Inject
	public CensusRetriever(@ConfigProperty(name = "openjdk.census.url", defaultValue = CENSUS_URL)
	String censusUrl) {
		this.censusUri = URI.create(censusUrl);
		this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
	}

	public String retrieveCensusContent() throws IOException, InterruptedException {
		Log.info("Retrieving OpenJDK census data...");
		HttpRequest request = HttpRequest.newBuilder().uri(censusUri).timeout(TIMEOUT).GET().build();

		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				Log.info("Successfully retrieved OpenJDK census data");
				return response.body();
			} else {
				Log.error("Failed to retrieve OpenJDK census data. Status code: " + response.statusCode());
				throw new IOException("Failed to retrieve census data. Status code: " + response.statusCode());
			}
		} catch (IOException | InterruptedException e) {
			Log.error("Error while retrieving OpenJDK census data", e);
			throw e;
		}
	}

	public URI getSourceUri() {
		return censusUri;
	}

	@PreDestroy
	public void close() {
		httpClient.close();
	}
}
