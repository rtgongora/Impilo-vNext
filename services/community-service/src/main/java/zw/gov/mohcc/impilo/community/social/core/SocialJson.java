package zw.gov.mohcc.impilo.community.social.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny helper for converting between JSON columns (JSONB) and Java records.
 * Defensive: never throws on bad JSON; returns sensible empty defaults.
 */
final class SocialJson {

    private SocialJson() {}

    static List<String> readStringList(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    static List<Map<String, Object>> readListOfMaps(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    static Map<String, Object> readMap(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    static String writeJson(ObjectMapper mapper, Object value) {
        if (value == null) return "{}";
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    static Map<String, Integer> toIntegerMap(JsonNode node) {
        Map<String, Integer> out = new HashMap<>();
        if (node == null || !node.isObject()) return out;
        node.fields().forEachRemaining(e -> {
            if (e.getValue().isNumber()) out.put(e.getKey(), e.getValue().intValue());
        });
        return out;
    }
}
