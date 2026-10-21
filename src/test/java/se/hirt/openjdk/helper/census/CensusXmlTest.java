/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.census;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CensusXmlTest {
	@Test
	void readsPublishedSchemaIncludingUnicodeAndRoles() throws IOException {
		OpenjdkCensusScraper scraper = new OpenjdkCensusScraper(fixture());
		try {
			assertEquals(8, scraper.getPeople().size());
			assertEquals("Alice Åström", scraper.getPeople().get("alice").getFullName());
			assertEquals("Example Organization", scraper.getPeople().get("hirt").getOrganization());
			assertEquals("Lead", scraper.getProjects().get("jmc").getMembers().get("hirt"));
			assertEquals("Member", scraper.getGroups().get("vulnerability").getMembers().get("hirt"));
			assertTrue(scraper.getPeople().get("unaffiliated").getAffiliations().isEmpty());
			assertEquals("JDK Mission Control Project", scraper.getProjects().get("jmc").getFullName());
			assertEquals("2026-10-20T15:14:43Z", scraper.getSnapshotMetadata().get("sourceTime"));
		} finally {
			scraper.shutdown();
		}
	}

	@Test
	void keepsSnapshotAndReportsStaleAfterInvalidRefreshThenRecovers() throws IOException {
		AtomicReference<String> content = new AtomicReference<>(fixture());
		CensusRetriever retriever = new CensusRetriever("http://127.0.0.1/census.xml") {
			@Override
			public String retrieveCensusContent() {
				return content.get();
			}
		};
		OpenjdkCensusScraper scraper = new OpenjdkCensusScraper(retriever, 3600);
		try {
			Map<String, Object> before = scraper.getSnapshotMetadata();
			content.set("<html>Temporary proxy error</html>");
			scraper.refresh();
			assertEquals(8, scraper.getPeople().size());
			assertEquals(before.get("lastSuccessfulRefresh"),
					scraper.getSnapshotMetadata().get("lastSuccessfulRefresh"));
			assertEquals(true, scraper.getSnapshotMetadata().get("stale"));
			assertEquals(true, scraper.getSnapshotMetadata().get("lastRefreshFailed"));
			content.set(fixture());
			scraper.refresh();
			assertEquals(false, scraper.getSnapshotMetadata().get("stale"));
			assertEquals(false, scraper.getSnapshotMetadata().get("lastRefreshFailed"));
		} finally {
			scraper.shutdown();
			retriever.close();
		}
	}

	@Test
	void rejectsUnknownReferencesAndTruncatedXml() throws IOException {
		String xml = fixture();
		assertThrows(IllegalArgumentException.class,
				() -> new OpenjdkCensusScraper(xml.replace("ref=\"hirt\"", "ref=\"unknown\"")));
		assertThrows(IllegalArgumentException.class, () -> new OpenjdkCensusScraper(xml.replace("</census>", "")));
	}

	private static String fixture() throws IOException {
		try (var input = CensusXmlTest.class.getResourceAsStream("/census-small.xml")) {
			assertNotNull(input);
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
