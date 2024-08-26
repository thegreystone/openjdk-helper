package se.hirt.openjdk.helper.census;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.io.IOException;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CensusRetriever {
	private static final String CENSUS_URL = "https://openjdk.org/census";
	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private final HttpClient httpClient;

	public CensusRetriever() {
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(TIMEOUT)
				.build();
	}

	public String retrieveCensusContent() throws IOException, InterruptedException {
		Log.info("Retrieving OpenJDK census data...");
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(CENSUS_URL))
				.timeout(TIMEOUT)
				.GET()
				.build();

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
}
