package zw.gov.mohcc.impilo.experience.workhome;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Small JSON-shape helpers shared by the Work Home family adapters (Phase E4). */
final class WorkHomeJson {

    private WorkHomeJson() {}

    /** Unwraps a bare array, {@code {items:[...]}}, {@code {data:[...]}} or {@code {content:[...]}} envelope. */
    static List<JsonNode> asNodeList(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        JsonNode candidate = node;
        if (node.isObject() && node.has("items") && node.get("items").isArray()) {
            candidate = node.get("items");
        } else if (node.isObject() && node.has("data") && node.get("data").isArray()) {
            candidate = node.get("data");
        } else if (node.isObject() && node.has("content") && node.get("content").isArray()) {
            candidate = node.get("content");
        }
        if (!candidate.isArray()) {
            return node.isObject() ? List.of(node) : List.of();
        }
        List<JsonNode> out = new ArrayList<>();
        candidate.forEach(out::add);
        return out;
    }

    static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    static String text(JsonNode node, String field, String fallback) {
        String s = text(node, field);
        return s != null ? s : fallback;
    }
}
