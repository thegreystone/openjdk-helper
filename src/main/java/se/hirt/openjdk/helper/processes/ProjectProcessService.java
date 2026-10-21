/* Copyright (C) 2026 Marcus Hirt. See LICENSE for license details. */
package se.hirt.openjdk.helper.processes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import se.hirt.openjdk.helper.census.OpenjdkCensusScraper;
import se.hirt.openjdk.helper.common.ApiSupport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class ProjectProcessService {
    private final JsonNode catalog;
    private final OpenjdkCensusScraper census;

    @Inject
    public ProjectProcessService(ObjectMapper json, OpenjdkCensusScraper census) {
        this.census = census;
        try (var input = getClass().getResourceAsStream("/project-processes.json")) {
            if (input == null) throw new IOException("Missing project process catalog");
            catalog = json.readTree(input);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load project process catalog", e);
        }
    }

    public Map<String, Object> getProjectProcesses(String projectId, String topic) {
        if (topic != null && topic.length() > 100) throw ApiSupport.badRequest("topic must be at most 100 characters");
        String filter = topic == null ? "" : topic.trim().toLowerCase(Locale.ROOT);
        List<JsonNode> entries = new ArrayList<>();
        List<String> topics = new ArrayList<>();
        for (JsonNode entry : catalog.path("processes")) {
            topics.add(entry.path("id").asText());
            if (filter.isEmpty() || (entry.path("id").asText() + " " + entry.path("title").asText())
                    .toLowerCase(Locale.ROOT).contains(filter)) entries.add(entry.deepCopy());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviewedOn", catalog.path("reviewedOn").asText());
        result.put("scope", catalog.path("scope").asText());
        result.put("topics", topics);
        result.put("results", entries);
        if (projectId != null && !projectId.isBlank()) {
            String id = ApiSupport.requireName(projectId, "projectid");
            var project = census.getProjects().get(id);
            if (project == null) throw new NotFoundException("Unknown census project: " + id);
            result.put("project", Map.of("id", id, "name", project.getFullName(),
                    "censusUrl", "https://openjdk.org/census#" + id));
            result.put("censusMetadata", census.getSnapshotMetadata());
            result.put("projectContext", "Use getProjectMembers for exact census roles. Use getContributionContext to resolve the repository's census project; repository names need not match project IDs.");
        }
        return result;
    }
}
