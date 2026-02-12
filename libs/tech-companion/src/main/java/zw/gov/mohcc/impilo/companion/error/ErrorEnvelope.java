package zw.gov.mohcc.impilo.companion.error;

import java.util.List;
import java.util.Map;

/**
 * Canonical v1.1 error response envelope.
 *
 * Wire format:
 * <pre>
 * {
 *   "error": {
 *     "code": "MISSING_REQUIRED_HEADER",
 *     "message": "Human readable message",
 *     "details": { ... },
 *     "request_id": "uuid",
 *     "correlation_id": "uuid"
 *   }
 * }
 * </pre>
 */
public record ErrorEnvelope(ErrorBody error) {

    public record ErrorBody(
            String code,
            String message,
            Map<String, Object> details,
            String request_id,
            String correlation_id
    ) {
    }

    public static ErrorEnvelope of(String code, String message,
                                   Map<String, Object> details,
                                   String requestId, String correlationId) {
        return new ErrorEnvelope(new ErrorBody(
                code, message,
                details != null ? details : Map.of(),
                requestId, correlationId));
    }

    public static ErrorEnvelope of(String code, String message,
                                   String requestId, String correlationId) {
        return of(code, message, Map.of(), requestId, correlationId);
    }
}
