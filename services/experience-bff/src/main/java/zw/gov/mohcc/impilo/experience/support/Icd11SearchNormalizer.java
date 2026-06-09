package zw.gov.mohcc.impilo.experience.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalizes search-service ICD-11 hits into UI-friendly rows. */
public final class Icd11SearchNormalizer {

    private Icd11SearchNormalizer() {}

    public static List<Map<String, Object>> normalize(JsonNode searchPayload) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (searchPayload == null || searchPayload.isNull()) {
            return out;
        }
        JsonNode hits = searchPayload.isArray() ? searchPayload : searchPayload.path("results");
        if (!hits.isArray()) {
            return out;
        }
        for (JsonNode hit : hits) {
            JsonNode content = hit.path("contentJson");
            String code = firstText(content, "code", "icd11_code", "icdCode");
            if (code == null || code.isBlank()) {
                code = hit.path("entityId").asText(null);
            }
            String description = firstText(content, "title", "display", "description", "name");
            if (description == null || description.isBlank()) {
                description = hit.path("searchableText").asText("");
            }
            if (code == null && description.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", code != null ? code : "");
            row.put("description", description);
            row.put("chapter", firstText(content, "chapter", "chapterTitle", "category"));
            row.put("score", hit.has("score") ? hit.get("score").asDouble() : 0.0);
            out.add(row);
        }
        return out;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && !node.get(field).isNull()) {
                String text = node.get(field).asText("").trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }
}
