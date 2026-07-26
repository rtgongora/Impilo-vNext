package zw.gov.mohcc.impilo.procedures.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.procedures.core.ProceduresDomainException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Maps domain exceptions to honest HTTP responses with a stable error envelope, so callers
 * surface real failures rather than hiding them behind empty success states.
 */
@RestControllerAdvice(basePackages = "zw.gov.mohcc.impilo.procedures.api")
public class ProceduresExceptionHandler {

    @ExceptionHandler(ProceduresDomainException.class)
    public ResponseEntity<Map<String, Object>> domain(ProceduresDomainException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatus());
        return body(status != null ? status : HttpStatus.UNPROCESSABLE_ENTITY, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoSuchElementException e) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException e) {
        return body(HttpStatus.CONFLICT, "ILLEGAL_STATE", e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of(
                        "code", code,
                        "message", message != null ? message : status.getReasonPhrase()),
                "timestamp", OffsetDateTime.now().toString()));
    }
}
