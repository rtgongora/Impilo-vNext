package zw.gov.mohcc.impilo.msikaflow.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.msikaflow.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.msikaflow.core.UpstreamUnavailableException;
import zw.gov.mohcc.impilo.msikaflow.core.ValidationFailedException;

import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", ex.getMessage(), 400, UUID.randomUUID().toString()));
    }

    /** 422 with structured per-item details in {@code data}; state untouched by contract. */
    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationFailed(ValidationFailedException ex) {
        log.warn("Validation failed [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiResponse<>(false, ex.getDetails(),
                        new ApiResponse.ApiError(ex.getCode(), ex.getMessage(), 422),
                        UUID.randomUUID().toString(), Instant.now()));
    }

    /** 502 — an upstream dependency is unreachable; never faked as success. */
    @ExceptionHandler(UpstreamUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUpstreamUnavailable(UpstreamUnavailableException ex) {
        log.error("Upstream unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error("UPSTREAM_UNAVAILABLE", ex.getMessage(), 502, UUID.randomUUID().toString()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(IllegalStateException ex) {
        log.warn("State conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("STATE_CONFLICT", ex.getMessage(), 409, UUID.randomUUID().toString()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", msg, 400, UUID.randomUUID().toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred", 500, UUID.randomUUID().toString()));
    }
}
