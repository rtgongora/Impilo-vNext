package zw.gov.mohcc.impilo.companion.filter;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorCodes;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthorityViolationException;
import zw.gov.mohcc.impilo.companion.timeout.TimeoutExceededException;

import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for v1.1 companion exceptions.
 *
 * Catches:
 * - {@link FederationAuthorityViolationException} => 403
 * - {@link TimeoutExceededException} => 504
 *
 * Services can extend this or register it as-is.
 */
@RestControllerAdvice
public class CompanionExceptionHandler {

    @ExceptionHandler(FederationAuthorityViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleFederationViolation(
            FederationAuthorityViolationException ex) {
        RequestContext ctx = safeContext();
        ErrorEnvelope envelope = ErrorEnvelope.of(
                ErrorCodes.FEDERATION_AUTHORITY_VIOLATION,
                ex.getMessage(),
                Map.of("pod_id", ex.getPodId()),
                ctx.requestId(), ctx.correlationId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(envelope);
    }

    @ExceptionHandler(TimeoutExceededException.class)
    public ResponseEntity<ErrorEnvelope> handleTimeout(TimeoutExceededException ex) {
        RequestContext ctx = safeContext();
        ErrorEnvelope envelope = ErrorEnvelope.of(
                ErrorCodes.CLIENT_TIMEOUT_EXCEEDED,
                ex.getMessage(),
                Map.of("timeout_ms", ex.getTimeoutMs()),
                ctx.requestId(), ctx.correlationId());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(envelope);
    }

    private static RequestContext safeContext() {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx != null) return ctx;
        // Fallback with generated IDs
        return RequestContext.of(
                "unknown", "unknown",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                null, null, null);
    }
}
