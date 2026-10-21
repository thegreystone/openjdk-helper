/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the generated OpenAPI document against the constraints of the OpenAI Custom GPT action
 * importer, which rejects object schemas without properties and array schemas without items.
 */
final class OpenApiAssertions {
	private OpenApiAssertions() {
	}

	static void verifyImportable(JsonNode document) {
		List<String> violations = new ArrayList<>();
		document.path("paths").properties().forEach(path -> path.getValue().properties().forEach(operation -> {
			JsonNode schema = operation.getValue().path("responses").path("200").path("content")
					.path("application/json").path("schema");
			collect(path.getKey() + " " + operation.getKey() + " 200 schema", schema, violations);
		}));
		document.path("components").path("schemas").properties()
				.forEach(entry -> collect("components.schemas." + entry.getKey(), entry.getValue(), violations));
		assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
	}

	private static void collect(String context, JsonNode schema, List<String> violations) {
		if (!schema.isObject()) {
			return;
		}
		String type = schema.path("type").asText("");
		JsonNode properties = schema.path("properties");
		if ("object".equals(type) && !(properties.isObject() && !properties.isEmpty())) {
			violations.add(context + ": object schema missing properties");
		}
		if ("array".equals(type) && !schema.has("items")) {
			violations.add(context + ": array schema missing items");
		}
		properties.properties().forEach(entry -> collect(context + "." + entry.getKey(), entry.getValue(), violations));
		collect(context + "[]", schema.path("items"), violations);
	}
}
