/*
 * Copyright (C) 2024-2026 Marcus Hirt
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

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(CensusTestResource.class)
public class CensusResourceTest {

	@Test
	public void testSearchPeopleEndpoint() {
		given().when().get("/census/people/search?query=Hir.*").then().statusCode(200).body("query", is("Hir.*"))
				.body("results", hasSize(3)).body("results[0].userId", notNullValue()).body("results[0].fullName", notNullValue())
				.body("results[0].affiliations.groups", isA(java.util.List.class))
				.body("results[0].affiliations.projects", isA(java.util.List.class));
	}

	@Test
	public void testSearchPeopleEndpointNoResults() {
		given().when().get("/census/people/search?query=NonexistentPerson").then().statusCode(200).body("query", is("NonexistentPerson"))
				.body("results", hasSize(0));
	}

	@Test
	public void testSearchPeopleEndpointMissingQuery() {
		given().when().get("/census/people/search").then().statusCode(400).body("error", is("Query parameter is required"));
	}

	@Test
	public void testSearchPeopleEndpointEmptyQuery() {
		given().when().get("/census/people/search?query=").then().statusCode(400).body("error", is("Query parameter is required"));
	}
	
	@Test
	public void testGetSinglePerson() {
		given().when().get("/census/people?userid=hirt").then().statusCode(200)
				.body("userid", is("hirt"))
				.body("results", hasSize(1))
				.body("results[0].userId", is("hirt"))
				.body("results[0].fullName", notNullValue())
				.body("not_found", is(nullValue()));
	}
	
	@Test
	public void testGetSinglePersonNotFound() {
		given().when().get("/census/people?userid=idontexist").then().statusCode(404)
				.body("error", is("Could not find person with userid idontexist"));
	}
	
	@Test
	public void testGetMultiplePersons() {
		given().when().get("/census/people?userid=hirt,aptmac").then().statusCode(200)
				.body("userid", is("hirt,aptmac"))
				.body("results", hasSize(2))
				.body("results.userId", hasItems("hirt", "aptmac"))
				.body("not_found", is(nullValue()));
	}
	
	@Test
	public void testGetMultiplePersonsWithSomeNotFound() {
		given().when().get("/census/people?userid=hirt,aptmac,idontexist").then().statusCode(200)
				.body("userid", is("hirt,aptmac,idontexist"))
				.body("results", hasSize(2))
				.body("results.userId", hasItems("hirt", "aptmac"))
				.body("not_found", hasSize(1))
				.body("not_found", hasItem("idontexist"));
	}
	
	@Test
	public void testGetMultiplePersonsAllNotFound() {
		given().when().get("/census/people?userid=idontexist1,idontexist2").then().statusCode(404)
				.body("error", is("Could not find any people with the provided userids: idontexist1,idontexist2"));
	}

	@Test
	public void testInvalidSearchPatternsReturnBadRequest() {
		for (String collection : new String[] {"people", "groups", "projects"}) {
			given().queryParam("query", "[").when().get("/census/" + collection + "/search")
					.then().statusCode(400).body("error", startsWith("Invalid regular expression:"));
		}
	}

	@Test
	public void testNotFoundErrorIsValidJsonForQuotedInput() {
		given().queryParam("userid", "missing\"user").when().get("/census/people")
				.then().statusCode(404).body("error", is("Could not find person with userid missing\"user"));
	}

	@Test
	public void testProjectAndGroupDetails() {
		given().queryParam("projectid", "jmc").when().get("/census/projects")
				.then().statusCode(200).body("results[0].name", is("JDK Mission Control Project"))
				.body("results[0].members.find { it.userId == 'hirt' }.role", is("Lead"));
		given().queryParam("groupid", "vulnerability").when().get("/census/groups")
				.then().statusCode(200).body("results[0].name", is("Vulnerability Group"))
				.body("results[0].members.find { it.userId == 'hirt' }.role", is("Member"));
	}

	@Test
	public void testBoundedExactRoleMembershipAndLiteralSearch() {
		given().queryParam("roles", "ReVieWer").queryParam("limit", 1).when().get("/census/projects/jmc/members")
				.then().statusCode(200).body("total", is(2)).body("hasMore", is(true))
				.body("results", hasSize(1)).body("results[0].userId", is("alice"))
				.body("results[0].role", is("Reviewer")).body("metadata.format", is("xml"));
		given().queryParam("roles", "reviewer").queryParam("limit", 1).queryParam("offset", 1)
				.when().get("/census/projects/jmc/members").then().statusCode(200)
				.body("results[0].userId", is("aptmac")).body("hasMore", is(false));
		given().queryParam("query", "ÅSTRÖM").when().get("/census/projects/jmc/members")
				.then().statusCode(200).body("results", hasSize(1)).body("results[0].userId", is("alice"));
		given().queryParam("query", ".*").when().get("/census/projects/jmc/members")
				.then().statusCode(200).body("results", hasSize(0));
	}

	@Test
	public void testMembersValidateBoundsAndUnknownRoles() {
		given().queryParam("limit", 101).when().get("/census/projects/jmc/members").then().statusCode(400);
		given().queryParam("offset", -1).when().get("/census/projects/jmc/members").then().statusCode(400);
		given().queryParam("roles", "expert").when().get("/census/projects/jmc/members").then().statusCode(400);
		given().when().get("/census/projects/unknown/members").then().statusCode(404);
	}

	@Test
	public void testCensusMetadata() {
		given().when().get("/census/status").then().statusCode(200)
				.body("available", is(true)).body("stale", is(false)).body("format", is("xml"))
				.body("sourceTime", is("2026-10-20T15:14:43Z")).body("lastSuccessfulRefresh", notNullValue())
				.body("sourceUrl", startsWith("http://127.0.0.1:"));
	}
}
