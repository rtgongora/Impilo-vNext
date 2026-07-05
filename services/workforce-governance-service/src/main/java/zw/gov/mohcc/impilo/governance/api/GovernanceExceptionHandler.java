package zw.gov.mohcc.impilo.governance.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates invalid-request conditions into proper HTTP 400 responses.
 *
 * <p>Without this, a missing or malformed {@code X-Tenant-ID} trust header (the
 * shared {@code TrustContextFilter} parses an unparseable tenant to {@code null},
 * after which the controllers throw {@link IllegalArgumentException}) surfaced as a
 * generic HTTP 500 — which read as "service broken" in audits rather than
 * "caller sent a bad request". The same applies to invalid enum/JSON input
 * (e.g. {@code allowed_target_types}).</p>
 */
@RestControllerAdvice
public class GovernanceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GovernanceExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.debug("Governance bad request: {}", ex.getMessage());
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "INVALID_REQUEST");
        error.put("message", ex.getMessage() != null ? ex.getMessage() : "Invalid request");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Illegal lifecycle transitions (e.g. two-person rule not yet satisfied,
     * executing a non-APPROVED platform action) are caller-resolvable conflicts,
     * not server faults — surface as HTTP 409 rather than a generic 500.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        log.debug("Governance state conflict: {}", ex.getMessage());
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "INVALID_STATE");
        error.put("message", ex.getMessage() != null ? ex.getMessage() : "Invalid state");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
