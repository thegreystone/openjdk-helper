/* Copyright (C) 2024-2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import se.hirt.openjdk.helper.census.CensusRetriever;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

public class CensusRetrieverTest {

	@Test
	public void retrievesConfiguredCensus() throws IOException, InterruptedException {
		try (CensusTestResource fixture = new CensusTestResource()) {
			CensusRetriever retriever = new CensusRetriever(fixture.start().get("openjdk.census.url"));
			try {
				String html = retriever.retrieveCensusContent();
				assertTrue(html.contains("Marcus Hirt"));
				assertTrue(html.contains("OpenJDK"));
			} finally {
				retriever.close();
			}
		}
	}

	@Test
	public void rejectsUpstreamErrors() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/census", exchange -> {
			exchange.sendResponseHeaders(503, -1);
			exchange.close();
		});
		server.start();
		CensusRetriever retriever = new CensusRetriever("http://127.0.0.1:" + server.getAddress().getPort() + "/census");
		try {
			IOException failure = assertThrows(IOException.class, retriever::retrieveCensusContent);
			assertTrue(failure.getMessage().contains("503"));
		} finally {
			retriever.close();
			server.stop(0);
		}
	}
}
