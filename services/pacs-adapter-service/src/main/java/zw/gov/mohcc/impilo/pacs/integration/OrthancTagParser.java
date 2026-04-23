package zw.gov.mohcc.impilo.pacs.integration;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Reads Orthanc simplified-tags JSON nodes (hex tag keys, Value field string or array).
 */
public final class OrthancTagParser {

    private OrthancTagParser() {
    }

    public static String stringTag(JsonNode tags, String hexTag) {
        if (tags == null || !tags.has(hexTag)) {
            return null;
        }
        JsonNode wrapper = tags.get(hexTag);
        JsonNode valueNode = wrapper.get("Value");
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isTextual()) {
            return valueNode.asText();
        }
        if (valueNode.isArray() && valueNode.size() > 0) {
            JsonNode first = valueNode.get(0);
            if (first != null && first.isTextual()) {
                return first.asText();
            }
            if (first != null && first.isNumber()) {
                return first.asText();
            }
        }
        if (valueNode.isNumber()) {
            return valueNode.asText();
        }
        return null;
    }

    public static Integer intTag(JsonNode tags, String hexTag) {
        String s = stringTag(tags, hexTag);
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int intTagOr(JsonNode tags, String hexTag, int defaultValue) {
        Integer v = intTag(tags, hexTag);
        return v != null ? v : defaultValue;
    }
}
