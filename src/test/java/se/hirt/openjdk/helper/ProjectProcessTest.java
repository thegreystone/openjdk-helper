/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(CensusTestResource.class)
class ProjectProcessTest {
    @Test void includesStepsAndOfficialLinksWithProjectContext() {
        given().queryParam("projectid", "jmc").queryParam("topic", "committer")
                .get("/processes").then().statusCode(200)
                .body("project.id", equalTo("jmc"))
                .body("results.size()", equalTo(1))
                .body("results[0].summary", containsString("Lazy Consensus"))
                .body("results[0].steps", not(empty()))
                .body("results[0].links[0].url", equalTo("https://openjdk.org/projects/#committer-vote"));
        given().queryParam("topic", "reviewer").get("/processes").then().statusCode(200)
                .body("results[0].summary", containsString("Three-Vote Consensus"));
    }
    @Test void catalogWorksWithoutAProjectAndInvalidProjectsAreExplicit() {
        given().get("/processes").then().statusCode(200)
                .body("results.size()", equalTo(10)).body("reviewedOn", equalTo("2026-10-21"));
        given().queryParam("topic", "no such process").get("/processes").then().statusCode(200)
                .body("results", empty());
        given().queryParam("projectid", "no-such-project").get("/processes").then().statusCode(404);
        given().queryParam("projectid", "../bad").get("/processes").then().statusCode(400);
    }
}
