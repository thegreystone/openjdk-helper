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
package se.hirt.openjdk.helper.census;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ServiceUnavailableException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Caches the OpenJDK census and refreshes it periodically.
 */
@Startup
@ApplicationScoped
public class OpenjdkCensusScraper {
	private volatile Set<String> userIds;
	private volatile Map<String, Group> groups;
	private volatile Map<String, Project> projects;
	private volatile Map<String, Person> people;
	private Instant sourceTime;
	private Instant lastSuccessfulRefresh;
	private Instant lastRefreshAttempt;
	private boolean lastRefreshFailed;
	private String format;
	private final int refreshIntervalInSeconds;
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
		this.refreshIntervalInSeconds = refreshIntervalInSeconds;
		refresh();
		scheduler.scheduleWithFixedDelay(this::refresh, refreshIntervalInSeconds, refreshIntervalInSeconds, TimeUnit.SECONDS);
	}

	/**
	 * For testing purposes _only_.
	 *
	 * @param html
	 * 		the HTML to use when testing.
	 */
	public OpenjdkCensusScraper(String html) {
		censusRetriever = null;
		refreshIntervalInSeconds = 3600;
		initialize(html);
	}

	void refresh() {
		synchronized (this) {
			lastRefreshAttempt = Instant.now();
		}
		try {
			initialize(censusRetriever.retrieveCensusContent());
		} catch (InterruptedException e) {
			markRefreshFailed();
			Thread.currentThread().interrupt();
			Log.warn("Census refresh interrupted", e);
		} catch (IOException | RuntimeException e) {
			markRefreshFailed();
			// Keep the last valid snapshot, and allow the next scheduled refresh to retry.
			Log.error("Could not refresh the OpenJDK census", e);
		}
	}

	private synchronized void markRefreshFailed() {
		lastRefreshFailed = true;
	}

	private void initialize(String content) {
		boolean xml = content.stripLeading().startsWith("<?xml") || content.stripLeading().startsWith("<census");
		Document doc = xml ? Jsoup.parse(content, "", Parser.xmlParser()) : Jsoup.parse(content);
		Map<String, Group> newGroups;
		Map<String, Project> newProjects;
		Map<String, Person> newPeople;
		Set<String> newUserIds;
		Instant newSourceTime = null;
		if (xml) {
			Element root = doc.selectFirst("census");
			if (root == null || content.contains("<!DOCTYPE") || !content.stripTrailing().endsWith("</census>")) {
				throw new IllegalArgumentException("Invalid census XML");
			}
			if (!root.attr("time").isBlank()) {
				newSourceTime = Instant.parse(root.attr("time"));
			}
			newPeople = extractXmlPeople(root);
			newGroups = new HashMap<>();
			newProjects = new HashMap<>();
			for (Element element : root.children()) {
				Affiliation affiliation;
				if (element.tagName().equals("group")) {
					affiliation = new Group(requiredAttribute(element, "name"));
					newGroups.put(affiliation.getId(), (Group) affiliation);
				} else if (element.tagName().equals("project")) {
					affiliation = new Project(requiredAttribute(element, "name"));
					newProjects.put(affiliation.getId(), (Project) affiliation);
				} else {
					continue;
				}
				Element name = element.selectFirst("full-name");
				if (name == null || name.text().isBlank()) {
					throw new IllegalArgumentException("Missing census affiliation name");
				}
				// Keep the display names used by the original HTML API.
				affiliation.setFullName(name.text() + (affiliation instanceof Group ? " Group" : " Project"));
				for (Element member : element.children()) {
					if (!member.tagName().equals("person")) {
						continue;
					}
					String userId = requiredAttribute(member, "ref");
					Person person = newPeople.get(userId);
					if (person == null) {
						throw new IllegalArgumentException("Unknown census member: " + userId);
					}
					String role = member.hasAttr("role") ? member.attr("role") : "member";
					if (!Set.of("member", "lead", "author", "committer", "reviewer").contains(role)) {
						throw new IllegalArgumentException("Unknown census role: " + role);
					}
					role = role.substring(0, 1).toUpperCase(Locale.ROOT) + role.substring(1);
					affiliation.addMember(userId, role);
					person.addAffiliation(affiliation.getId(), affiliation);
				}
			}
			newUserIds = new HashSet<>(newPeople.keySet());
		} else {
			newUserIds = extractUserIds(doc);
			newGroups = extractGroups(doc);
			newProjects = extractProjects(doc);
			newPeople = extractPeople(doc, newGroups, newProjects);
		}
		if (newUserIds.isEmpty() || newGroups.isEmpty() || newProjects.isEmpty() || newPeople.isEmpty()) {
			throw new IllegalArgumentException("The response does not contain a complete OpenJDK census");
		}

		synchronized (this) {
			userIds = newUserIds;
			groups = newGroups;
			projects = newProjects;
			people = newPeople;
			sourceTime = newSourceTime;
			lastSuccessfulRefresh = Instant.now();
			lastRefreshAttempt = lastSuccessfulRefresh;
			lastRefreshFailed = false;
			format = xml ? "xml" : "html";
		}
		// Print results
		Log.info("OpenJDK groups found: " + groups.size());
		Log.info("OpenJDK projects found: " + projects.size());
		Log.info("OpenJDK people found: " + userIds.size());
	}

	public synchronized Set<String> getUserIds() {
		ensureAvailable();
		return new HashSet<>(userIds);
	}

	public synchronized Map<String, Group> getGroups() {
		ensureAvailable();
		return new HashMap<>(groups);
	}

	public synchronized Map<String, Project> getProjects() {
		ensureAvailable();
		return new HashMap<>(projects);
	}

	public synchronized Map<String, Person> getPeople() {
		ensureAvailable();
		return new HashMap<>(people);
	}

	private static Map<String, Person> extractXmlPeople(Element root) {
		Map<String, Person> result = new HashMap<>();
		for (Element element : root.children()) {
			if (!element.tagName().equals("person")) {
				continue;
			}
			String id = requiredAttribute(element, "name");
			Element name = element.selectFirst("full-name");
			if (name == null || name.text().isBlank()) {
				throw new IllegalArgumentException("Missing census person name");
			}
			Person person = new Person(id, name.text());
			Element organization = element.selectFirst("org");
			if (organization != null) {
				person.setOrganization(organization.text());
			}
			if (result.put(id, person) != null) {
				throw new IllegalArgumentException("Duplicate census person: " + id);
			}
		}
		return result;
	}

	private static String requiredAttribute(Element element, String name) {
		String value = element.attr(name);
		if (value.isBlank()) {
			throw new IllegalArgumentException("Missing census attribute: " + name);
		}
		return value;
	}

	public synchronized Map<String, Object> getSnapshotMetadata() {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("sourceUrl", censusRetriever == null ? "fixture" : censusRetriever.getSourceUri().toString());
		result.put("format", format);
		result.put("sourceTime", sourceTime == null ? null : sourceTime.toString());
		result.put("lastSuccessfulRefresh", lastSuccessfulRefresh == null ? null : lastSuccessfulRefresh.toString());
		result.put("lastRefreshAttempt", lastRefreshAttempt == null ? null : lastRefreshAttempt.toString());
		result.put("available", people != null);
		result.put("stale", lastSuccessfulRefresh == null || lastRefreshFailed
				|| Duration.between(lastSuccessfulRefresh, Instant.now()).getSeconds() > refreshIntervalInSeconds * 2L);
		result.put("lastRefreshFailed", lastRefreshFailed);
		return result;
	}

	/** Captures related data and provenance together before callers filter outside the lock. */
	public synchronized Snapshot getSnapshot() {
		ensureAvailable();
		return new Snapshot(Map.copyOf(groups), Map.copyOf(projects), Map.copyOf(people), getSnapshotMetadata());
	}

	public record Snapshot(Map<String, Group> groups, Map<String, Project> projects,
			Map<String, Person> people, Map<String, Object> metadata) {}

	private void ensureAvailable() {
		if (people == null) {
			throw new ServiceUnavailableException("OpenJDK census data is not available yet");
		}
	}

	public Set<Person> findPeople(String regexp) {
		Pattern pattern = Pattern.compile(regexp);
		Predicate<Person> predicate = person -> pattern.matcher(person.getFullName()).find() || pattern.matcher(person.getUserid()).find();
		return getPeople().values().stream().filter(predicate).collect(Collectors.toSet());
	}

	public Set<Project> findProjects(String regexp) {
		Pattern pattern = Pattern.compile(regexp);
		Predicate<Project> predicate = project -> pattern.matcher(project.getFullName()).find() || pattern.matcher(project.getId()).find();
		return getProjects().values().stream().filter(predicate).collect(Collectors.toSet());
	}

	public Set<Group> findGroups(String regexp) {
		Pattern pattern = Pattern.compile(regexp);
		Predicate<Group> predicate = group -> pattern.matcher(group.getFullName()).find() || pattern.matcher(group.getId()).find();
		return getGroups().values().stream().filter(predicate).collect(Collectors.toSet());
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

	@PreDestroy
	public void shutdown() {
		Log.info("Shutting down census updater...");
		scheduler.shutdown();
	}
}
