package zw.gov.mohcc.impilo.inpatient.api.controller;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.inpatient.core.BedSafetyViolationException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates domain exceptions into structured, client-meaningful HTTP responses so the
 * experience layer can surface them honestly (e.g. an unsafe bed placement or a bed that is
 * no longer available), rather than a generic 500. Body shape matches the inpatient bed
 * controller's existing errors: {@code {"error": {"code": ..., "message": ...}}}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BedSafetyViolationException.class)
    public ResponseEntity<Map<String, Object>> bedUnsafe(BedSafetyViolationException ex) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "BED_UNSAFE");
        error.put("message", ex.getMessage());
        error.put("violations", ex.getViolations());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", error));
    }

    /**
     * A concurrent edit of the same aggregate (Wave 6 §18) — the JPA @Version guard
     * detected a stale write. Surfaced as a clean 409 STALE_WRITE so the experience layer
     * can prompt the user to reload the latest state, rather than silently losing an update
     * or returning a generic 500. Covers Spring's OptimisticLockingFailureException, which
     * wraps JPA's ObjectOptimisticLockingFailureException / OptimisticLockException.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> staleWrite(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", Map.of("code", "STALE_WRITE",
                        "message", "This record was changed by someone else. Reload the latest version and retry.")));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", Map.of("code", "CONFLICT", "message", safeMessage(ex))));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", Map.of("code", "BAD_REQUEST", "message", safeMessage(ex))));
    }

    private static String safeMessage(Exception ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
