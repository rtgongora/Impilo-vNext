package zw.gov.mohcc.impilo.abis.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.abis.core.QualityBelowThresholdException;

import java.util.Map;

/**
 * Error contract for the ABIS API. Never leaks template bytes or stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("validation failed");
        return ResponseEntity.badRequest().body(Map.of("error", "VALIDATION_FAILED", "message", detail));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, String>> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_REQUIRED_HEADER", "message", e.getHeaderName() + " header is required"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_REQUEST", "message", String.valueOf(e.getMessage())));
    }

    /** Enrol below the governed per-modality quality minimum — rejected (fail-closed). */
    @ExceptionHandler(QualityBelowThresholdException.class)
    public ResponseEntity<Map<String, Object>> handleQuality(QualityBelowThresholdException e) {
        return ResponseEntity.unprocessableEntity().body(Map.of(
                "error", "QUALITY_BELOW_THRESHOLD",
                "message", e.getMessage(),
                "qualityScore", e.getQualityScore(),
                "minQuality", e.getMinQuality()));
    }

    /**
     * Governance/workflow conflict — e.g. merging without a CONFIRMED_DUPLICATE decision,
     * a dual-control violation, or acting on an already-resolved case.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "CONFLICT", "message", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        log.error("Unhandled ABIS API error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "INTERNAL_ERROR", "message", "Unexpected error"));
    }
}
