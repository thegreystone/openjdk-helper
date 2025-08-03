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
package se.hirt.openjdk.helper.census;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class CensusService {
	@Inject
	OpenjdkCensusScraper census;

	public Response getPeople(String userIdParam) {
		Map<String, Person> allPeople = census.getPeople();
		Collection<Person> people = new HashSet<>();
		List<String> notFoundIds = new ArrayList<>();

		if (userIdParam == null || userIdParam.trim().isEmpty()) {
			people = allPeople.values();
		} else {
			String[] userIds = userIdParam.split(",");

			for (String userId : userIds) {
				String trimmedId = userId.trim();
				if (!trimmedId.isEmpty()) {
					Person person = allPeople.get(trimmedId);
					if (person != null) {
						people.add(person);
					} else {
						notFoundIds.add(trimmedId);
					}
				}
			}
			if (people.isEmpty()) {
				if (userIds.length == 1) {
					return Response.status(Response.Status.NOT_FOUND)
							.entity("{\"error\": \"Could not find person with userid " + userIdParam + "\"}").build();
				} else {
					return Response.status(Response.Status.NOT_FOUND)
							.entity("{\"error\": \"Could not find any people with the provided userids: " + userIdParam + "\"}").build();
				}
			}
		}

		Map<String, Object> result = new HashMap<>();
		result.put("userid", userIdParam);
		if (!notFoundIds.isEmpty()) {
			result.put("not_found", notFoundIds);
		}
		result.put("results", people.stream().map(CensusService::personToMap).collect(Collectors.toList()));
		return Response.ok(result).build();
	}

	public Response searchPeople(String query) {
		if (query == null || query.trim().isEmpty()) {
			return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\": \"Query parameter is required\"}").build();
		}

		Set<Person> people = census.findPeople(query);
		Map<String, Object> result = new HashMap<>();
		result.put("query", query);
		result.put("results", people.stream().map(CensusService::personToMap).collect(Collectors.toList()));

		return Response.ok(result).build();
	}

	public Response getProjects(String projectId) {
		Map<String, Project> allProjects = census.getProjects();
		Collection<Project> projects;
		if (projectId == null || projectId.trim().isEmpty()) {
			projects = allProjects.values();
		} else {
			projects = new HashSet<>();
			Project project = allProjects.get(projectId.trim());
			if (project == null) {
				return Response.status(Response.Status.NOT_FOUND)
						.entity("{\"error\": \"Could not find project with projectid " + projectId + "\"}").build();
			}
			projects.add(project);
		}

		Map<String, Object> result = new HashMap<>();
		result.put("projectid", projectId);
		result.put("results", projects.stream().map(CensusService::projectToMap).collect(Collectors.toList()));
		return Response.ok(result).build();
	}

	public Response searchProjects(String query) {
		if (query == null || query.trim().isEmpty()) {
			return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\": \"Query parameter is required\"}").build();
		}

		Set<Project> projects = census.findProjects(query);
		Map<String, Object> result = new HashMap<>();
		result.put("query", query);
		result.put("results", projects.stream().map(CensusService::projectToMap).collect(Collectors.toList()));
		return Response.ok(result).build();
	}

	public Response getGroups(String groupId) {
		Map<String, Group> allGroups = census.getGroups();
		Collection<Group> groups;
		if (groupId == null || groupId.trim().isEmpty()) {
			groups = allGroups.values();
		} else {
			groups = new HashSet<>();
			Group group = allGroups.get(groupId.trim());
			if (group == null) {
				return Response.status(Response.Status.NOT_FOUND)
						.entity("{\"error\": \"Could not find group with groupid " + groupId + "\"}").build();
			}
			groups.add(group);
		}

		Map<String, Object> result = new HashMap<>();
		result.put("groupid", groupId);
		result.put("results", groups.stream().map(CensusService::groupToMap).collect(Collectors.toList()));

		return Response.ok(result).build();
	}

	public Response searchGroups(String query) {
		if (query == null || query.trim().isEmpty()) {
			return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\": \"Query parameter is required\"}").build();
		}

		Set<Group> groups = census.findGroups(query);
		Map<String, Object> result = new HashMap<>();
		result.put("query", query);
		result.put("results", groups.stream().map(CensusService::groupToMap).collect(Collectors.toList()));

		return Response.ok(result).build();
	}

	public static Map<String, Object> personToMap(Person person) {
		Map<String, Object> personMap = new HashMap<>();
		personMap.put("userId", person.getUserid());
		personMap.put("fullName", person.getFullName());

		Map<String, List<Map<String, String>>> affiliations = new HashMap<>();
		affiliations.put("groups", new ArrayList<>());
		affiliations.put("projects", new ArrayList<>());

		for (Map.Entry<String, Affiliation> entry : person.getAffiliations().entrySet()) {
			Affiliation affiliation = entry.getValue();
			Map<String, String> affiliationMap = new HashMap<>();
			affiliationMap.put("id", affiliation.getId());
			affiliationMap.put("name", affiliation.getFullName());
			affiliationMap.put("role", affiliation.getMembers().get(person.getUserid()));

			if (affiliation instanceof Group) {
				affiliations.get("groups").add(affiliationMap);
			} else if (affiliation instanceof Project) {
				affiliations.get("projects").add(affiliationMap);
			}
		}
		personMap.put("affiliations", affiliations);
		return personMap;
	}

	public static Map<String, Object> projectToMap(Project project) {
		Map<String, Object> projectMap = new HashMap<>();
		projectMap.put("id", project.getId());
		projectMap.put("name", project.getFullName());
		projectMap.put("members", project.getMembers().entrySet().stream().map(entry -> {
			Map<String, String> memberMap = new HashMap<>();
			memberMap.put("userId", entry.getKey());
			memberMap.put("role", entry.getValue());
			return memberMap;
		}).collect(Collectors.toList()));
		return projectMap;
	}

	public static Map<String, Object> groupToMap(Group group) {
		Map<String, Object> groupMap = new HashMap<>();
		groupMap.put("id", group.getId());
		groupMap.put("name", group.getFullName());
		groupMap.put("members", group.getMembers().entrySet().stream().map(entry -> {
			Map<String, String> memberMap = new HashMap<>();
			memberMap.put("userId", entry.getKey());
			memberMap.put("role", entry.getValue());
			return memberMap;
		}).collect(Collectors.toList()));
		return groupMap;
	}
}
