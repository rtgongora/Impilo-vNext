package zw.gov.mohcc.impilo.experience.resilience;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for the BFF — ensures no stack traces leak to clients
 * and all errors follow the structured error format.
 */
@RestControllerAdvice
public class BffGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BffGlobalExceptionHandler.class);

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleServiceUnavailable(
            ServiceUnavailableException e, HttpServletRequest request) {

        log.warn("503 — {} unavailable: {}", e.getServiceName(), e.getReason());

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "SERVICE_UNAVAILABLE");
        error.put("service", e.getServiceName());
        error.put("message", e.getReason());
        error.put("request_id", request.getHeader("X-Request-ID"));
        error.put("correlation_id", request.getHeader("X-Correlation-ID"));

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", error));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            IllegalArgumentException e, HttpServletRequest request) {

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "BAD_REQUEST");
        error.put("message", e.getMessage());
        error.put("request_id", request.getHeader("X-Request-ID"));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(
            Exception e, HttpServletRequest request) {

        if (e instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.BAD_REQUEST;
            }
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", "HTTP_ERROR");
            error.put("message", rse.getReason() != null ? rse.getReason() : status.getReasonPhrase());
            error.put("request_id", request.getHeader("X-Request-ID"));
            error.put("correlation_id", request.getHeader("X-Correlation-ID"));
            return ResponseEntity.status(status).body(Map.of("error", error));
        }

        log.error("500 — unhandled exception: {}", e.getMessage(), e);

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "INTERNAL_ERROR");
        error.put("message", "An unexpected error occurred");
        error.put("request_id", request.getHeader("X-Request-ID"));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", error));
    }
}
