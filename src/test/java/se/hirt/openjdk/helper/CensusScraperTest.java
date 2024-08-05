/*
 * Copyright (C) 2024 Marcus Hirt
 *                    www.hirt.se
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
 *
 * Copyright (C) Marcus Hirt, 2024
 */
package se.hirt.openjdk.helper;

import io.quarkus.test.junit.QuarkusTest;
import org.apache.groovy.json.internal.IO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class CensusScraperTest {

	@Test
	public void testGetUserIds() throws IOException {
		String html = getHtml();
		OpenjdkCensusScraper scraper = new OpenjdkCensusScraper(html);
		Set<String> users = scraper.getUserIds();
		assertNotNull(users);
		System.out.println(users);
		assertEquals(1231, users.size());
	}

	@Test
	public void testGetPeople() throws IOException {
		String html = getHtml();
		OpenjdkCensusScraper scraper = new OpenjdkCensusScraper(html);
		Map<String, Person> people = scraper.getPeople();
		assertNotNull(people);
		assertEquals(1231, people.keySet().size());
		Set<Person> noAffiliation = new HashSet<>();
		for (Person person : people.values()) {
			if (person.getAffiliations().size() <= 0) {
				noAffiliation.add(person);
			}
		}
		assertEquals(102, noAffiliation.size());
	}

	@Test
	public void testGetProjects() throws IOException {
		String html = getHtml();
		OpenjdkCensusScraper scraper = new OpenjdkCensusScraper(html);
		Map<String, Project> projects = scraper.getProjects();
		assertNotNull(projects);
		System.out.println(projects);
		assertEquals(78, projects.size());
	}

	@Test
	public void testGetGroups() throws IOException {
		String html = getHtml();
		OpenjdkCensusScraper scraper = new OpenjdkCensusScraper(html);
		Map<String, Group> groups = scraper.getGroups();
		assertNotNull(groups);
		System.out.println(groups);
		assertEquals(20, groups.size());
	}

	@Test
	public void testGetJMCProject() throws IOException {
		String html = getHtml();
		OpenjdkCensusScraper scraper = new OpenjdkCensusScraper(html);
		Map<String, Project> projects = scraper.getProjects();
		assertNotNull(projects);
		Project project = projects.get("jmc");
		assertTrue(project.getFullName().contains("JDK Mission Control"));
		assertEquals(27, project.getMembers().size());
	}

	@Test
	public void testFindPeople() throws IOException {
		String html = getHtml();
		OpenjdkCensusScraper scraper = new OpenjdkCensusScraper(html);
		Set<Person> people = scraper.findPeople("Hir.*");
		assertEquals(3, people.size());
	}

	@Test
	public void testLiveScraper() throws IOException {
		OpenjdkCensusScraper scraper = new OpenjdkCensusScraper(new CensusRetriever(), 1000);
		Set<Person> people = scraper.findPeople("Hir.*");
		assertEquals(3, people.size());
		scraper.shutdown();
	}

	private String getHtml() throws IOException {
		String html;
		try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("census.html")) {
			if (inputStream == null) {
				throw new IOException("Could not find census.html");
			}
			html = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		}
		return html;
	}
}
