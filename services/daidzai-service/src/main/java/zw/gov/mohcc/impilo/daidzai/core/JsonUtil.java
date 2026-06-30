package zw.gov.mohcc.impilo.daidzai.core;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Tiny JSON helper for jsonb columns / payloads. */
public final class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private JsonUtil() {}

    public static String toJson(Object value) {
        if (value == null) return null;
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
