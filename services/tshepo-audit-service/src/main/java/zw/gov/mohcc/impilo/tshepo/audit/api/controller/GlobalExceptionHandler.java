package zw.gov.mohcc.impilo.tshepo.audit.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for the audit service.
 * All errors are returned in the standardized ApiResponse envelope.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation failed: {}", details);

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", details, 400,
                        UUID.randomUUID().toString()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getHeaderName());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("MISSING_HEADER",
                        "Required header missing: " + ex.getHeaderName(), 400,
                        UUID.randomUUID().toString()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("TYPE_MISMATCH",
                        "Invalid value for parameter '" + ex.getName() + "'", 400,
                        UUID.randomUUID().toString()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("BAD_REQUEST", ex.getMessage(), 400,
                        UUID.randomUUID().toString()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.error("Illegal state: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", ex.getMessage(), 500,
                        UUID.randomUUID().toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR",
                        "An unexpected error occurred", 500,
                        UUID.randomUUID().toString()));
    }
}
