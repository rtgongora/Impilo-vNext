package zw.gov.mohcc.impilo.khuluma.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Maps Khuluma core exceptions to stable HTTP semantics for the BFF: not-found → 404,
 * membership/authorization → 403, validation → 400, missing trust context → 401.
 */
@RestControllerAdvice(assignableTypes = {KhulumaController.class})
public class KhulumaExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoSuchElementException ex) {
        return error(HttpStatus.NOT_FOUND, "not_found", ex.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> forbidden(SecurityException ex) {
        return error(HttpStatus.FORBIDDEN, "forbidden", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "bad_request", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> unauthorized(IllegalStateException ex) {
        return error(HttpStatus.UNAUTHORIZED, "unauthorized", ex.getMessage());
    }

    @ExceptionHandler(zw.gov.mohcc.impilo.khuluma.core.MeetingMediaUnavailableException.class)
    public ResponseEntity<Map<String, Object>> mediaUnavailable(
            zw.gov.mohcc.impilo.khuluma.core.MeetingMediaUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "media_unavailable", ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("error", Map.of(
                "code", code, "message", message == null ? code : message)));
    }
}
