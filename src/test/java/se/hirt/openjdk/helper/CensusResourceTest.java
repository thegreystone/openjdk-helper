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
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class CensusResourceTest {

	@Test
	public void testSearchPeopleEndpoint() {
		given()
				.when().get("/census/people/search?query=Hir.*")
				.then()
				.statusCode(200)
				.body("query", is("Hir.*"))
				.body("results", hasSize(3))
				.body("results[0].userId", notNullValue())
				.body("results[0].fullName", notNullValue())
				.body("results[0].affiliations.groups", isA(java.util.List.class))
				.body("results[0].affiliations.projects", isA(java.util.List.class));
	}

	@Test
	public void testSearchPeopleEndpointNoResults() {
		given()
				.when().get("/census/people/search?query=NonexistentPerson")
				.then()
				.statusCode(200)
				.body("query", is("NonexistentPerson"))
				.body("results", hasSize(0));
	}

	@Test
	public void testSearchPeopleEndpointMissingQuery() {
		given()
				.when().get("/census/people/search")
				.then()
				.statusCode(400)
				.body("error", is("Query parameter is required"));
	}

	@Test
	public void testSearchPeopleEndpointEmptyQuery() {
		given()
				.when().get("/census/people/search?query=")
				.then()
				.statusCode(400)
				.body("error", is("Query parameter is required"));
	}
}
