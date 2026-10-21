/*
 * Copyright (C) 2026 Marcus Hirt
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
package se.hirt.openjdk.helper;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class McpProtocolAssertions {
	private McpProtocolAssertions() {
	}

	static void verify(McpTestClient client) throws Exception {
		JsonNode initialized = result(client.initialize());
		assertEquals(McpTestClient.PROTOCOL_VERSION, initialized.path("protocolVersion").asText());
		assertEquals("OpenJDK Helper", initialized.path("serverInfo").path("name").asText());
		String version = initialized.path("serverInfo").path("version").asText();
		assertFalse(version.isBlank());
		assertFalse(version.contains("${"), "Maven version placeholder must not leak into MCP metadata");
		assertTrue(initialized.path("capabilities").has("tools"));

		JsonNode listing = result(client.request("tools/list", null));
		Set<String> names = new HashSet<>();
		for (JsonNode tool : listing.path("tools")) {
			names.add(tool.path("name").asText());
			assertEquals("object", tool.path("inputSchema").path("type").asText());
			assertTrue(tool.path("annotations").path("readOnlyHint").asBoolean(), tool.toString());
			// Claude Code rejects a tools/list whose annotations.title is JSON null; it must be absent or a string.
			JsonNode title = tool.path("annotations").path("title");
			assertTrue(title.isMissingNode() || title.isTextual(), tool.toString());
			if (tool.path("name").asText().equals("getPerson")) {
				assertEquals("string",
						tool.path("inputSchema").path("properties").path("userid").path("type").asText());
				assertTrue(tool.path("inputSchema").path("required").toString().contains("userid"));
			}
		}
		assertEquals(Set.of("getVersion", "searchPeople", "getPerson", "searchProjects", "getProject", "searchGroups",
				"getGroup", "getGitHubRepos", "getGitHubPulls", "getProjectMembers", "getCensusStatus",
				"getContributionContext", "getBuildGuidance", "getOpenJdkReview", "getBackportStatus", "getIssue",
				"searchIssues", "getJep", "searchJeps", "getProjectProcesses", "getIssueFixStatus",
				"getPullRequestTriage", "getContributorRecord", "getProjectActivity", "getReleaseSchedule",
				"searchMailingList", "searchDevelopersGuide"), names);
		assertFalse(listing.hasNonNull("nextCursor"), "All tools should fit on one page");
		assertEquals(version, text(client.call("getVersion", Map.of()), false));

		JsonNode person = jsonText(client.call("getPerson", Map.of("userid", "hirt")));
		assertEquals("hirt", person.path("results").get(0).path("userId").asText());
		assertEquals("Marcus Hirt", person.path("results").get(0).path("fullName").asText());
		JsonNode partial = jsonText(client.call("getPerson", Map.of("userid", "hirt,no-such-openjdk-user")));
		assertEquals(1, partial.path("results").size());
		assertEquals("no-such-openjdk-user", partial.path("not_found").get(0).asText());
		assertTrue(text(client.call("getPerson", Map.of("userid", "no-such-openjdk-user")), true)
				.contains("no-such-openjdk-user"));

		assertTrue(jsonText(client.call("searchPeople", Map.of("query", "Hirt"))).path("results").size() > 0);
		assertEquals(0,
				jsonText(client.call("searchPeople", Map.of("query", "no-such-openjdk-user"))).path("results").size());
		assertTrue(jsonText(client.call("getProject", Map.of("projectid", "jmc"))).path("results").size() > 0);
		assertTrue(jsonText(client.call("searchProjects", Map.of("query", "jmc"))).path("results").size() > 0);
		assertTrue(jsonText(client.call("getGroup", Map.of("groupid", "vulnerability"))).path("results").size() > 0);
		assertTrue(jsonText(client.call("searchGroups", Map.of("query", "vulnerability"))).path("results").size() > 0);
		text(client.call("searchPeople", Map.of("query", "")), true);
		text(client.call("getGitHubRepos", Map.of("user", "")), true);
		text(client.call("getGitHubPulls", Map.of("owner", "", "repo", "")), true);

		assertEquals("xml", jsonText(client.call("getCensusStatus", Map.of())).path("format").asText());
		assertEquals(1, jsonText(client.call("getProjectMembers", Map.of("projectId", "jmc", "roles", "lead")))
				.path("total").asInt());
		Map<String, Object> repository = Map.of("owner", "openjdk", "repo", "jdk17u", "ref", "master");
		assertEquals("jdk-updates",
				jsonText(client.call("getContributionContext", repository)).path("projectId").asText());
		assertTrue(jsonText(client.call("getBuildGuidance", repository)).path("documents").get(0).path("available")
				.asBoolean());
		assertEquals("jmc", jsonText(client.call("getGitHubRepos", Map.of("user", "openjdk"))).path("items").get(0)
				.path("name").asText());
		assertTrue(jsonText(
				client.call("getGitHubPulls", Map.of("owner", "openjdk", "repo", "jmc", "user", "hirt", "limit", 10)))
				.path("items").get(0).path("body").asText().contains("### Progress"));
		var review = jsonText(client.call("getOpenJdkReview", Map.of("owner", "openjdk", "repo", "jmc", "number", 42)));
		assertEquals("complete", review.path("status").asText());
		assertTrue(review.path("reviews").get(0).path("atCurrentHead").asBoolean());
		assertEquals("Reviewer", review.path("reviews").get(0).path("skaraRole").asText());
		assertEquals("aptmac", review.path("skaraReviewers").get(0).path("openjdkId").asText());
		assertEquals("not_assessed", review.path("integrationReadiness").asText());
		var backport = jsonText(client.call("getBackportStatus",
				Map.of("owner", "openjdk", "repo", "jmc", "commit", "a".repeat(40), "targets", "openjdk/jmc@master")));
		assertEquals("found", backport.path("targets").get(0).path("status").asText());
		assertEquals("JMC-8639",
				jsonText(client.call("getIssue", Map.of("key", "JMC-8639"))).path("issue").path("key").asText());
		assertEquals(1, jsonText(client.call("searchIssues", Map.of("project", "JMC"))).path("total").asInt());
		assertTrue(text(client.call("getIssue", Map.of("key", "JMC-403")), true).contains("accessDenied"));
		assertEquals("Fixture feature",
				jsonText(client.call("getJep", Map.of("number", 517))).path("jep").path("title").asText());
		assertEquals(1, jsonText(client.call("searchJeps", Map.of("release", "26"))).path("total").asInt());
		assertTrue(jsonText(client.call("getProjectProcesses", Map.of("projectid", "jmc", "topic", "committer")))
				.path("results").get(0).path("summary").asText().contains("Lazy Consensus"));

		var fixStatus = jsonText(client.call("getIssueFixStatus", Map.of("key", "JMC-8639")));
		assertEquals("JMC-8639", fixStatus.path("issue").path("key").asText());
		assertEquals("9.1.1", fixStatus.path("fixedIn").get(0).asText());
		assertTrue(jsonText(client.call("searchIssues", Map.of("project", "JMC", "type", "Bug", "fixVersion", "10")))
				.path("jql").asText().contains("issuetype = \"Bug\" AND fixVersion = \"10\""));
		var triage = jsonText(client.call("getPullRequestTriage", Map.of("owner", "openjdk", "repo", "jmc")));
		assertEquals(1, triage.path("groups").path("awaitingReview").path("count").asInt());
		assertEquals(1, triage.path("labelCounts").path("rfr").asInt());
		var record = jsonText(
				client.call("getContributorRecord", Map.of("owner", "openjdk", "repo", "jmc", "login", "hirt")));
		assertEquals(1, record.path("integratedPullRequests").path("total").asInt());
		assertEquals(0, record.path("openPullRequests").asInt());
		var activity = jsonText(client.call("getProjectActivity",
				Map.of("owner", "openjdk", "repo", "jmc", "since", "2026-07-01", "until", "2026-09-30")));
		assertEquals(1, activity.path("integrated").path("total").asInt());
		assertEquals("hirt", activity.path("firstTimeContributors").get(0).asText());
		var schedule = jsonText(client.call("getReleaseSchedule", Map.of("release", 26)));
		assertEquals(5, schedule.path("milestones").size());
		assertEquals("2026-03-17", schedule.path("generalAvailability").asText());
		var mail = jsonText(
				client.call("searchMailingList", Map.of("list", "jmc-dev", "month", "2026-02", "query", "CFV")));
		assertEquals(2, mail.path("total").asInt());
		assertEquals("vote", mail.path("results").get(0).path("kind").asText());
		var guide = jsonText(client.call("searchDevelopersGuide", Map.of("query", "backport")));
		assertEquals("backporting", guide.path("results").get(0).path("id").asText());
		assertTrue(text(client.call("getReleaseSchedule", Map.of("release", 1)), true).contains("release must be"));

		// Missing arguments are tool validation errors; an unknown tool is a JSON-RPC error.
		assertTrue(text(client.call("getPerson", Map.of()), true).contains("userid"));
		assertTrue(client.call("noSuchTool", Map.of()).has("error"));
	}

	private static JsonNode jsonText(JsonNode response) throws Exception {
		return McpTestClient.JSON.readTree(text(response, false));
	}

	private static String text(JsonNode response, boolean error) {
		JsonNode result = result(response);
		assertEquals(error, result.path("isError").asBoolean(false), response.toString());
		assertEquals("text", result.path("content").get(0).path("type").asText());
		return result.path("content").get(0).path("text").asText();
	}

	private static JsonNode result(JsonNode response) {
		assertFalse(response.has("error"), response.toString());
		assertTrue(response.has("result"), response.toString());
		return response.get("result");
	}
}
