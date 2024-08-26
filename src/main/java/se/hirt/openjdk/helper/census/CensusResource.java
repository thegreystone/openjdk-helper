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

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import java.util.*;
import java.util.stream.Collectors;

@Path("/census")
public class CensusResource {
	@Inject
	OpenjdkCensusScraper census;

	@GET
	@Path("/people")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPeople(
			@QueryParam("userid")
			@Parameter(description = "Lists the person with the userid, or all people in OpenJDK if userid isn't provided.", example = "hirt")
			String userId) {
		Map<String, Person> allPeople = census.getPeople();
		Collection<Person> people = null;
		if (userId == null || userId.trim().isEmpty()) {
			people = allPeople.values();
		} else {
			people = new HashSet<>();
			Person person = allPeople.get(userId.trim());
			if (person == null) {
				return Response.status(Response.Status.NOT_FOUND).entity("{\"error\": \"Could not find person with userid " + userId + "\"}").build();
			}
			people.add(person);
		}

		Map<String, Object> result = new HashMap<>();
		result.put("userid", userId);
		result.put("results", people.stream().map(CensusResource::personToMap).collect(Collectors.toList()));
		return Response.ok(result).build();
	}

	@GET
	@Path("/people/search")
	@Produces(MediaType.APPLICATION_JSON)
	public Response searchPeople(
			@QueryParam("query")
			@Parameter(description = "Search query for people using a regular expression. Searches through both userid and full names.", example = ".*Hirt")
			String query) {
		if (query == null || query.trim().isEmpty()) {
			return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\": \"Query parameter is required\"}").build();
		}

		Set<Person> people = census.findPeople(query);
		Map<String, Object> result = new HashMap<>();
		result.put("query", query);
		result.put("results", people.stream().map(CensusResource::personToMap).collect(Collectors.toList()));

		return Response.ok(result).build();
	}

	@GET
	@Path("/projects")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getProjects(
			@QueryParam("projectid") @Parameter(description = "Lists the project for the projectid, or all projects if not provided.", example = "jmc") String projectId) {
		Map<String, Project> allProjects = census.getProjects();
		Collection<Project> projects = null;
		if (projectId == null || projectId.trim().isEmpty()) {
			projects = allProjects.values();
		} else {
			projects = new HashSet<>();
			Project project = allProjects.get(projectId.trim());
			if (project == null) {
				return Response.status(Response.Status.NOT_FOUND).entity("{\"error\": \"Could not find project with projectid " + projectId + "\"}").build();
			}
			projects.add(project);
		}

		Map<String, Object> result = new HashMap<>();
		result.put("projectid", projectId);
		result.put("results", projects.stream().map(CensusResource::projectToMap).collect(Collectors.toList()));
		return Response.ok(result).build();
	}

	@GET
	@Path("/projects/search")
	@Produces(MediaType.APPLICATION_JSON)
	public Response searchProjects(
			@QueryParam("query")
			@Parameter(description = "Search query for projects using a regular expression. Searches both projectid and project names.", example = ".*Mission.*")
			String query) {
		if (query == null || query.trim().isEmpty()) {
			return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\": \"Query parameter is required\"}").build();
		}

		Set<Project> projects = census.findProjects(query);
		Map<String, Object> result = new HashMap<>();
		result.put("query", query);
		result.put("results", projects.stream().map(CensusResource::projectToMap).collect(Collectors.toList()));
		return Response.ok(result).build();
	}

	@GET
	@Path("/groups")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getGroups(
			@QueryParam("groupid") @Parameter(description = "Lists the group for the groupid, or all if not provided.", example = "vulnerability") String groupId) {
		Map<String, Group> allGroups = census.getGroups();
		Collection<Group> groups = null;
		if (groupId == null || groupId.trim().isEmpty()) {
			groups = allGroups.values();
		} else {
			groups = new HashSet<>();
			Group group = allGroups.get(groupId.trim());
			if (group == null) {
				return Response.status(Response.Status.NOT_FOUND).entity("{\"error\": \"Could not find group with groupid " + groupId + "\"}").build();
			}
			groups.add(group);
		}

		Map<String, Object> result = new HashMap<>();
		result.put("groupid", groupId);
		result.put("results", groups.stream().map(CensusResource::groupToMap).collect(Collectors.toList()));

		return Response.ok(result).build();
	}

	@GET
	@Path("/groups/search")
	@Produces(MediaType.APPLICATION_JSON)
	public Response searchGroups(
			@QueryParam("query")
			@Parameter(description = "Search query for groups using a regular expression. Searches both groupid and group names.", example = ".*uln.*")
			String query) {
		if (query == null || query.trim().isEmpty()) {
			return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\": \"Query parameter is required\"}").build();
		}

		Set<Group> groups = census.findGroups(query);
		Map<String, Object> result = new HashMap<>();
		result.put("query", query);
		result.put("results", groups.stream().map(CensusResource::groupToMap).collect(Collectors.toList()));

		return Response.ok(result).build();
	}

	private static Map<String, Object> personToMap(Person person) {
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

	private static Map<String, Object> projectToMap(Project project) {
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

	private static Map<String, Object> groupToMap(Group group) {
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
