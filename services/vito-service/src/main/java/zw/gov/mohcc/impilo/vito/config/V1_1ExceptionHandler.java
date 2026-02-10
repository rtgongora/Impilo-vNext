package zw.gov.mohcc.impilo.vito.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import zw.gov.mohcc.impilo.vito.core.FederationAuthorityGuard.FederationNotAuthorizedException;

import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for v1.1-specific exceptions.
 * Produces the standard v1.1 error envelope format.
 */
@ControllerAdvice
public class V1_1ExceptionHandler {

    @ExceptionHandler(FederationNotAuthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleFederationNotAuthorized(
            FederationNotAuthorizedException ex, HttpServletRequest request) {

        String requestId = request.getHeader("X-Request-ID");
        String correlationId = request.getHeader("X-Correlation-ID");

        return ResponseEntity.status(403).body(Map.of(
                "error", Map.of(
                        "code", "FEDERATION_NOT_AUTHORIZED",
                        "message", "Only the national pod is authorized for merge operations",
                        "details", Map.of("pod_id", ex.getPodId() != null ? ex.getPodId() : "null"),
                        "request_id", requestId != null ? requestId : UUID.randomUUID().toString(),
                        "correlation_id", correlationId != null ? correlationId : UUID.randomUUID().toString()
                )
        ));
    }
}
