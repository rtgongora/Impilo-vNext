package zw.gov.mohcc.impilo.vito.config;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility to write v1.1 standard error JSON envelope.
 *
 * Format:
 * {
 *   "error": {
 *     "code": "STRING_ENUM",
 *     "message": "Human readable message",
 *     "details": { ... },
 *     "request_id": "uuid",
 *     "correlation_id": "uuid"
 *   }
 * }
 *
 * Pure servlet — no Spring MVC dependency.
 */
public final class V1_1ErrorWriter {

    private V1_1ErrorWriter() {
    }

    /**
     * Write a v1.1 error response with details map.
     */
    public static void writeError(HttpServletResponse response, int status, String code,
                                  String message, Map<String, Object> details,
                                  String requestId, String correlationId) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String reqId = (requestId != null && !requestId.isBlank()) ? requestId : UUID.randomUUID().toString();
        String corrId = (correlationId != null && !correlationId.isBlank()) ? correlationId : UUID.randomUUID().toString();

        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"error\":{");
        sb.append("\"code\":").append(jsonString(code)).append(',');
        sb.append("\"message\":").append(jsonString(message)).append(',');
        sb.append("\"details\":").append(toJsonObject(details)).append(',');
        sb.append("\"request_id\":").append(jsonString(reqId)).append(',');
        sb.append("\"correlation_id\":").append(jsonString(corrId));
        sb.append("}}");

        response.getWriter().write(sb.toString());
        response.getWriter().flush();
    }

    /**
     * Write a simple v1.1 error response without details.
     */
    public static void writeError(HttpServletResponse response, int status, String code,
                                  String message, String requestId, String correlationId) throws IOException {
        writeError(response, status, code, message, Map.of(), requestId, correlationId);
    }

    private static String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + escapeJson(value) + "\"";
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String toJsonObject(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(jsonString(entry.getKey())).append(':');
            Object val = entry.getValue();
            if (val instanceof String s) {
                sb.append(jsonString(s));
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
                        sb.append(jsonString(si));
                    } else {
                        sb.append(jsonString(String.valueOf(item)));
                    }
                }
                sb.append(']');
            } else {
                sb.append(jsonString(String.valueOf(val)));
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
