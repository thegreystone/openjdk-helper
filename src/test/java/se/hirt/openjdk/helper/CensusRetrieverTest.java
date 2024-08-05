package se.hirt.openjdk.helper;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class CensusRetrieverTest {

	@Inject
	CensusRetriever censusRetriever;

	@Test
	public void testRetrieveCensusContentLive() throws IOException, InterruptedException {
		String result = censusRetriever.retrieveCensusContent();
		assertNotNull(result);
		assertFalse(result.isEmpty());
		assertTrue(result.contains("Marcus Hirt"));
	}

	@Test
	public void testRetrieveCensusContentHasExpectedContent() throws IOException, InterruptedException {
		String result = censusRetriever.retrieveCensusContent();
		assertNotNull(result);
		assertFalse(result.isEmpty());
		assertTrue(result.contains("OpenJDK"));
	}
}
