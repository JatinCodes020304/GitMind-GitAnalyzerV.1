package com.gitmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

/** Bounds generated fields before persisting or rendering them. */
public final class AiInsights {
    private AiInsights() {}
    public static ObjectNode validate(JsonNode json) {
        if (!(json instanceof ObjectNode object)) throw new IllegalStateException("Invalid AI response");
        text(json.path("summary"), 1200);
        var focus = json.path("developerType");
        text(focus.path("label"), 100); text(focus.path("explanation"), 600);
        if (!Set.of("low", "medium", "high").contains(focus.path("confidence").asText()))
            throw new IllegalStateException("Invalid confidence");
        references(focus.path("repositories"));
        for (String field : Set.of("projectTypes", "strengths", "nextSteps")) {
            array(json.path(field), 4);
            for (JsonNode insight : json.path(field)) {
                text(insight.path("title"), 100); text(insight.path("detail"), 600);
                references(insight.path("repositories"));
            }
        }
        array(json.path("notableProjects"), 3);
        for (JsonNode project : json.path("notableProjects")) {
            text(project.path("repository"), 100); text(project.path("reason"), 600);
        }
        array(json.path("limitations"), 4);
        for (JsonNode limitation : json.path("limitations")) text(limitation, 400);
        return object;
    }
    private static void text(JsonNode node, int max) {
        if (!node.isTextual() || node.asText().isBlank() || node.asText().length() > max)
            throw new IllegalStateException("Invalid AI text");
    }
    private static void array(JsonNode node, int max) {
        if (!node.isArray() || node.size() > max) throw new IllegalStateException("Invalid AI list");
    }
    private static void references(JsonNode node) {
        array(node, 3);
        for (JsonNode reference : node) text(reference, 100);
    }
}
