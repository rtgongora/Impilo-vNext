package zw.gov.mohcc.impilo.experience.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
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
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleMalformedParams(
            Exception ex,
            HttpServletRequest request) {
        // MissingRequestHeaderException was NOT mapped, so a caller omitting a required trust
        // header got 500 INTERNAL_ERROR -- the BFF blaming itself for the caller's malformed
        // request. Found the moment the catch-all started logging what it had been discarding:
        // POST /internal/v1/nompilo/context was returning 500 for a missing header.
        String message;
        if (ex instanceof MissingServletRequestParameterException missing) {
            message = "Missing required parameter: " + missing.getParameterName();
        } else if (ex instanceof MissingRequestHeaderException header) {
            message = "Missing required header: " + header.getHeaderName();
        } else {
            message = "A request parameter has the wrong type";
        }
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

    /**
     * Renders a trust refusal as the canonical challenge envelope.
     *
     * <p>Registered ahead of the {@link ResponseStatusException} handler, which would otherwise
     * flatten every one of these to {@code HTTP_ERROR} plus the log sentence the governance service
     * happened to write.</p>
     *
     * <p>The status comes from the decision, not a constant: an outage answers 503 and a step-up
     * answers 401, so a client can act on the status alone even before it reads the body.</p>
     */
    @ExceptionHandler(zw.gov.mohcc.impilo.experience.trust.TrustChallengeException.class)
    public ResponseEntity<Map<String, Object>> handleTrustChallenge(
            zw.gov.mohcc.impilo.experience.trust.TrustChallengeException ex,
            HttpServletRequest request) {
        var outcome = ex.outcome();
        HttpStatus status = zw.gov.mohcc.impilo.experience.trust.TrustChallengeResponder
                .statusFor(outcome == null ? null : outcome.decision());
        log.warn("Trust challenge on {} {}: decision={} reason={}", request.getMethod(),
                request.getRequestURI(), outcome == null ? null : outcome.decision(),
                outcome == null ? null : outcome.reasonCode());

        String requestId = request.getHeader(CompanionHeaders.REQUEST_ID);
        String correlationId = request.getHeader(CompanionHeaders.CORRELATION_ID);
        if (requestId == null) requestId = UUID.randomUUID().toString();
        if (correlationId == null) correlationId = UUID.randomUUID().toString();

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", outcome == null || outcome.reasonCode() == null
                ? "TRUST_CHALLENGE" : outcome.reasonCode());
        // The user-facing text is a message KEY, never a sentence. The governance services' own
        // strings ("Tshepo PDP denied telemedicine read") are log lines; showing one to a person
        // both leaks the internal topology and tells them nothing they can act on.
        error.put("message", outcome == null || outcome.userMessageKey() == null
                ? "trust.deny.generic" : outcome.userMessageKey());
        // The whole canonical outcome, not a summary: reason code, permitted methods, required
        // assurance and the continuation are exactly what the shell needs to offer a next step,
        // and every one of them was being discarded here.
        error.put("details", Map.of("trust_challenge", outcome == null ? Map.of() : outcome));
        error.put("request_id", requestId);
        error.put("correlation_id", correlationId);
        return ResponseEntity.status(status).body(Map.of("error", error));
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
