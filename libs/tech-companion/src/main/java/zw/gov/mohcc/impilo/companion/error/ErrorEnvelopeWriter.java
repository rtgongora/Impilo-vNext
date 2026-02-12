package zw.gov.mohcc.impilo.companion.error;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single canonical implementation of v1.1 error envelope writing.
 *
 * Pure servlet — no Spring MVC dependency.
 * Replaces per-service V1_1ErrorWriter copies.
 */
public final class ErrorEnvelopeWriter {

    private ErrorEnvelopeWriter() {
    }

    /**
     * Write a v1.1 error response with details.
     */
    public static void write(HttpServletResponse response, int status,
                             String code, String message,
                             Map<String, Object> details,
                             String requestId, String correlationId) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String reqId = isPresent(requestId) ? requestId : UUID.randomUUID().toString();
        String corrId = isPresent(correlationId) ? correlationId : UUID.randomUUID().toString();

        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"error\":{");
        sb.append("\"code\":").append(jsonStr(code)).append(',');
        sb.append("\"message\":").append(jsonStr(message)).append(',');
        sb.append("\"details\":").append(toJson(details)).append(',');
        sb.append("\"request_id\":").append(jsonStr(reqId)).append(',');
        sb.append("\"correlation_id\":").append(jsonStr(corrId));
        sb.append("}}");

        response.getWriter().write(sb.toString());
        response.getWriter().flush();
    }

    /**
     * Write a simple v1.1 error response without details.
     */
    public static void write(HttpServletResponse response, int status,
                             String code, String message,
                             String requestId, String correlationId) throws IOException {
        write(response, status, code, message, Map.of(), requestId, correlationId);
    }

    // ── JSON serialization helpers (no external dependency) ─────

    private static boolean isPresent(String s) {
        return s != null && !s.isBlank();
    }

    private static String jsonStr(String value) {
        if (value == null) return "null";
        return "\"" + escape(value) + "\"";
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> sb.append(c);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(jsonStr(entry.getKey())).append(':');
            Object val = entry.getValue();
            if (val instanceof String s) {
                sb.append(jsonStr(s));
            } else if (val instanceof Number n) {
                sb.append(n);
            } else if (val instanceof Boolean b) {
                sb.append(b);
            } else if (val instanceof List<?> list) {
                sb.append('[');
                boolean listFirst = true;
                for (Object item : list) {
                    if (!listFirst) sb.append(',');
                    listFirst = false;
                    if (item instanceof String si) {
                        sb.append(jsonStr(si));
                    } else {
                        sb.append(jsonStr(String.valueOf(item)));
                    }
                }
                sb.append(']');
            } else if (val instanceof Map<?, ?> nested) {
                sb.append(toJson((Map<String, Object>) nested));
            } else {
                sb.append(jsonStr(String.valueOf(val)));
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
