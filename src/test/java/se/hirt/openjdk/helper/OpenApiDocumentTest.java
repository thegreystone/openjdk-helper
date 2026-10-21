/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(CensusTestResource.class)
class OpenApiDocumentTest {
	@Test
	void responseSchemasAreImportable() throws Exception {
		String body = given().accept("application/json").when().get("/openapi?format=json").then().statusCode(200)
				.extract().asString();
		JsonNode document = McpTestClient.JSON.readTree(body);
		assertTrue(document.path("paths").has("/github/triage"), body);
		assertTrue(document.path("paths").path("/github/triage").path("get").path("responses").path("200")
				.path("content").path("application/json").path("schema").path("properties").has("groups"), body);
		OpenApiAssertions.verifyImportable(document);
	}
}
