package zw.gov.mohcc.impilo.pacs.integration;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Reads values from Orthanc REST {@code MainDicomTags} objects (human-readable keys).
 */
public final class OrthancMainDicomHelper {

    private OrthancMainDicomHelper() {
    }

    public static String stringField(JsonNode mainDicomTags, String fieldName) {
        if (mainDicomTags == null || !mainDicomTags.has(fieldName)) {
            return null;
        }
        JsonNode n = mainDicomTags.get(fieldName);
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isTextual()) {
            return n.asText();
        }
        if (n.isObject() && n.has("Value")) {
            JsonNode v = n.get("Value");
            if (v != null && v.isTextual()) {
                return v.asText();
            }
            if (v != null && v.isArray() && v.size() > 0 && v.get(0).isTextual()) {
                return v.get(0).asText();
            }
        }
        if (n.isArray() && n.size() > 0 && n.get(0).isTextual()) {
            return n.get(0).asText();
        }
        return n.asText(null);
    }

    public static Integer intField(JsonNode mainDicomTags, String fieldName) {
        String s = stringField(mainDicomTags, fieldName);
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
