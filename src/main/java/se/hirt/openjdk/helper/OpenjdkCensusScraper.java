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

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generates Slogans.
 */
@Startup
@ApplicationScoped
public class OpenjdkCensusScraper {
	private volatile Set<String> userIds;
	private volatile Map<String, Group> groups;
	private volatile Map<String, Project> projects;
	private volatile Map<String, Person> people;
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	private final CensusRetriever censusRetriever;

	@Inject
	public OpenjdkCensusScraper(CensusRetriever censusRetriever) {
		this(censusRetriever, 3600);
	}

	public OpenjdkCensusScraper(CensusRetriever censusRetriever, int refreshIntervalInSeconds) {
		Log.info("Scraper Instantiated with a " + refreshIntervalInSeconds + " second interval refresh");
		// Immediately download and initialize.
		this.censusRetriever = censusRetriever;
		try {
			String html = censusRetriever.retrieveCensusContent();
			initialize(html);
		} catch (IOException | InterruptedException e) {
			Log.error(e);
		}

		scheduler.scheduleAtFixedRate(() -> {
			try {
				String html = censusRetriever.retrieveCensusContent();
				initialize(html);
			} catch (IOException | InterruptedException e) {
				Log.error(e);
			}
		}, refreshIntervalInSeconds, refreshIntervalInSeconds, TimeUnit.SECONDS);
	}

	/**
	 * For testing purposes _only_.
	 *
	 * @param html the HTML to use when testing.
	 */
	public OpenjdkCensusScraper(String html) {
		censusRetriever = new CensusRetriever();
		initialize(html);
	}

	private void initialize(String html) {
		Document doc = Jsoup.parse(html);

		Set<String> newUserIds = extractUserIds(doc);
		Map<String, Group> newGroups = extractGroups(doc);
		Map<String, Project> newProjects = extractProjects(doc);
		Map<String, Person> newPeople = extractPeople(doc, newGroups, newProjects);

		synchronized (this) {
			userIds = newUserIds;
			groups = newGroups;
			projects = newProjects;
			people = newPeople;
		}
		// Print results
		Log.info("OpenJDK groups found: " + groups.size());
		Log.info("OpenJDK projects found: " + projects.size());
		Log.info("OpenJDK people found: " + userIds.size());
	}

	public synchronized Set<String> getUserIds() {
		return new HashSet<>(userIds);
	}

	public synchronized Map<String, Group> getGroups() {
		return new HashMap<>(groups);
	}

	public synchronized Map<String, Project> getProjects() {
		return new HashMap<>(projects);
	}

	public synchronized Map<String, Person> getPeople() {
		return new HashMap<>(people);
	}

	public Set<Person> findPeople(String regexp) {
		Pattern pattern = Pattern.compile(regexp);
		Predicate<Person> predicate = person ->  pattern.matcher(person.getFullName()).find() || pattern.matcher(person.getUserid()).find();
		synchronized (this) {
			return people.values().stream().filter(predicate).collect(Collectors.toSet());
		}
	}

	public Set<Project> findProjects(String regexp) {
		Pattern pattern = Pattern.compile(regexp);
		Predicate<Project> predicate = project ->  pattern.matcher(project.getFullName()).find() || pattern.matcher(project.getId()).find();
		synchronized (this) {
			return projects.values().stream().filter(predicate).collect(Collectors.toSet());
		}
	}

	public Set<Group> findGroups(String regexp) {
		Pattern pattern = Pattern.compile(regexp);
		Predicate<Group> predicate = group ->  pattern.matcher(group.getFullName()).find() || pattern.matcher(group.getId()).find();
		synchronized (this) {
			return groups.values().stream().filter(predicate).collect(Collectors.toSet());
		}
	}


	private static Set<String> extractUserIds(Document doc) {
		Set<String> userIds = new HashSet<>();
		Element peopleSection = doc.select("td.hd > a[href=#people]").first();
		if (peopleSection != null) {
			Element useridCell = peopleSection.parent().nextElementSibling();
			if (useridCell != null) {
				Elements useridLinks = useridCell.select("a[href^=#]");
				for (Element link : useridLinks) {
					String userid = link.text();
					userIds.add(userid);
				}
			}
		}
		return userIds;
	}

	/**
	 * First pass - only id's and empty holders.
	 */
	private static Map<String, Group> extractGroups(Document doc) {
		Map<String, Group> groups = new HashMap<>();
		Element groupsSection = doc.select("td.hd > a[href=#groups]").first();
		if (groupsSection != null) {
			Element groupCell = groupsSection.parent().nextElementSibling();
			if (groupCell != null) {
				Elements groupLinks = groupCell.select("a[href^=#]");
				for (Element link : groupLinks) {
					String groupId = link.text();
					groups.put(groupId, new Group(groupId));
				}
			}
		}
		return groups;
	}

	/**
	 * First pass - only id's and empty holders.
	 */
	private static Map<String, Project> extractProjects(Document doc) {
		Map<String, Project> projects = new HashMap<>();
		Element groupsSection = doc.select("td.hd > a[href=#projects]").first();
		if (groupsSection != null) {
			Element groupCell = groupsSection.parent().nextElementSibling();
			if (groupCell != null) {
				Elements groupLinks = groupCell.select("a[href^=#]");
				for (Element link : groupLinks) {
					String projectId = link.text();
					projects.put(projectId, new Project(projectId));
				}
			}
		}
		return projects;
	}

	private static Map<String, Person> extractPeople(Document doc, Map<String, Group> groups, Map<String, Project> projects) {
		Map<String, Person> people = new HashMap<>();

		Element peopleSection = doc.select("tr.part#people").first();
		if (peopleSection == null) {
			return people; // Return empty map if people section not found
		}

		Element current = peopleSection.nextElementSibling();
		while (current != null && !current.hasClass("part")) {
			if (current.hasClass("section")) {
				String userId = current.id();
				String fullName = current.select("td > span").get(1).text();
				Person person = new Person(userId, fullName);
				people.put(userId, person);

				current = current.nextElementSibling();
				while (current != null && !current.hasClass("section") && !current.hasClass("part")) {
					if (current.hasClass("role")) {
						String role = current.select("td").get(1).text();
						current = current.nextElementSibling();
						while (current != null && !current.hasClass("role") && !current.hasClass("section") && !current.hasClass("part")) {
							Element linkElement = current.select("td > a").first();
							if (linkElement != null) {
								String affiliationId = linkElement.attr("href").substring(1);
								String entityRole = current.select("td").get(1).text();
								String[] parts = entityRole.split("–");
								String affiliationName = parts[0].trim();
								String roleName = parts.length > 1 ? parts[1].trim() : "Member";


								Affiliation affiliation = null;
								if (role.equals("Groups")) {
									affiliation = groups.get(affiliationId);
								} else if (role.equals("Projects")) {
									affiliation = projects.get(affiliationId);
								}
								updateAffiliation(affiliation, affiliationName, userId, roleName);
								person.addAffiliation(affiliationId, affiliation);
							}
							current = current.nextElementSibling();
						}
					} else {
						current = current.nextElementSibling();
					}
				}
			} else {
				current = current.nextElementSibling();
			}
		}
		return people;
	}

	private static void updateAffiliation(Affiliation affiliation, String fullName, String userId, String role) {
		affiliation.addMember(userId, role);

		if (affiliation.getFullName() == null || affiliation.getFullName().isBlank()) {
			affiliation.setFullName(fullName);
		}
	}

	public void shutdown() {
		Log.info("Shutting down census updater...");
		scheduler.shutdown();
	}
}
