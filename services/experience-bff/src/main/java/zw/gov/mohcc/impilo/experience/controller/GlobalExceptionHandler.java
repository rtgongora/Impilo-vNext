package zw.gov.mohcc.impilo.experience.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Request body validation failed", request);
    }

    /**
     * Unmapped routes (no controller handler / no static resource) are client errors, not server
     * faults. Without this, Spring's NoResourceFoundException/NoHandlerFoundException fall through
     * to the generic handler below and surface as 500 INTERNAL_ERROR, which misrepresents a
     * not-implemented BFF read path as a crash.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNoHandler(
            Exception ex,
            HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "No BFF endpoint for " + request.getMethod() + " " + request.getRequestURI(), request);
    }

    /**
     * Missing/mistyped request parameters are client errors. Both BFF advice
     * classes declare an Exception.class catch-all and their relative order is
     * unspecified, so this mapping must exist here as well as in
     * BffGlobalExceptionHandler or the 500 catch-all can shadow it.
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleMalformedParams(
            Exception ex,
            HttpServletRequest request) {
        String message = ex instanceof MissingServletRequestParameterException missing
                ? "Missing required parameter: " + missing.getParameterName()
                : "A request parameter has the wrong type";
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, request);
    }

    /**
     * OIDC protocol violations (expired/replayed transaction, nonce/issuer/audience
     * mismatch, failed code exchange) are governed client errors, not server crashes.
     * Without this mapping a replayed callback surfaced as 500 INTERNAL_ERROR — the
     * replay was still refused (fail-closed) but misreported. The exception's code is
     * a fixed safe constant; no token or credential material ever reaches the body.
     */
    @ExceptionHandler(zw.gov.mohcc.impilo.experience.auth.session.OidcSessionService.OidcProtocolException.class)
    public ResponseEntity<Map<String, Object>> handleOidcProtocol(
            zw.gov.mohcc.impilo.experience.auth.session.OidcSessionService.OidcProtocolException ex,
            HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.code(),
                "Authentication request could not be completed. Start a new sign-in.", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return buildErrorResponse(status, "HTTP_ERROR", message, request);
    }

    /**
     * Last-resort handler. It previously returned 500 and discarded the exception entirely -- no
     * log line, no stack, nothing. Every unexpected failure in the BFF became an opaque
     * "An unexpected error occurred" that could not be diagnosed from anywhere, so a real defect
     * could sit in the estate indefinitely looking like a transient blip. The broken browser OIDC
     * callback was exactly that: a 500 whose cause was unreadable in any log.
     *
     * <p>The RESPONSE stays deliberately generic -- an unexpected exception's message can carry
     * internal detail and must not reach a caller. The LOG carries the diagnosis, correlated by
     * request and correlation id.</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(
            Exception ex,
            HttpServletRequest request) {
        log.error("Unhandled exception: {} {} -> 500 [{}] requestId={} correlationId={}",
                request.getMethod(), request.getRequestURI(), ex.getClass().getName(),
                request.getHeader(CompanionHeaders.REQUEST_ID),
                request.getHeader(CompanionHeaders.CORRELATION_ID), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String code, String message, HttpServletRequest request) {

        String requestId = request.getHeader(CompanionHeaders.REQUEST_ID);
        String correlationId = request.getHeader(CompanionHeaders.CORRELATION_ID);

        if (requestId == null) requestId = UUID.randomUUID().toString();
        if (correlationId == null) correlationId = UUID.randomUUID().toString();

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("details", Map.of());
        error.put("request_id", requestId);
        error.put("correlation_id", correlationId);

        return ResponseEntity.status(status).body(Map.of("error", error));
    }
}
