package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonSupport {
    private JsonSupport() {}

    public static String toJsonSafe(ObjectMapper mapper, Object value, String defaultJson) {
        if (value == null) return defaultJson;
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return defaultJson;
        }
    }

    public static Object parseJsonSafe(ObjectMapper mapper, String json, Object defaultValue) {
        if (json == null || json.isBlank()) return defaultValue;
        try {
            return mapper.readValue(json, Object.class);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}

