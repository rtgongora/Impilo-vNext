package zw.gov.mohcc.impilo.pct.core;

import java.util.Map;
import java.util.UUID;

final class EdPayloadMapper {

    private EdPayloadMapper() {}

    static String str(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String key : keys) {
            Object v = body.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString().trim();
            }
        }
        return null;
    }

    static Integer integer(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String key : keys) {
            Object v = body.get(key);
            if (v == null) continue;
            if (v instanceof Number n) return n.intValue();
            try {
                return Integer.parseInt(v.toString().trim());
            } catch (NumberFormatException ignored) {
                /* try next key */
            }
        }
        return null;
    }

    static Boolean bool(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String key : keys) {
            Object v = body.get(key);
            if (v instanceof Boolean b) return b;
            if (v != null) return Boolean.parseBoolean(v.toString());
        }
        return null;
    }

    static UUID uuid(Map<String, Object> body, String... keys) {
        String s = str(body, keys);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
