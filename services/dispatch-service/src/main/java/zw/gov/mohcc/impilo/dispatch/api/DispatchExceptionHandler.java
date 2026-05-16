package zw.gov.mohcc.impilo.dispatch.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.dispatch.core.DispatchJobService;
import zw.gov.mohcc.impilo.dispatch.core.DeliveryOperationsService;
import zw.gov.mohcc.impilo.dispatch.core.InvalidStatusTransitionException;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class DispatchExceptionHandler {

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidTransition(InvalidStatusTransitionException ex) {
        RequestContext ctx = safeContext();
        ErrorEnvelope envelope = ErrorEnvelope.of(
                "INVALID_STATUS_TRANSITION",
                ex.getMessage(),
                Map.of("current_status", ex.getCurrentStatus(),
                        "requested_status", ex.getRequestedStatus()),
                ctx.requestId(), ctx.correlationId());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(envelope);
    }

    @ExceptionHandler(DispatchJobService.JobNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleJobNotFound(DispatchJobService.JobNotFoundException ex) {
        RequestContext ctx = safeContext();
        ErrorEnvelope envelope = ErrorEnvelope.of(
                "NOT_FOUND", ex.getMessage(),
                ctx.requestId(), ctx.correlationId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(envelope);
    }

    @ExceptionHandler(DeliveryOperationsService.DeliveryNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleDeliveryNotFound(DeliveryOperationsService.DeliveryNotFoundException ex) {
        RequestContext ctx = safeContext();
        ErrorEnvelope envelope = ErrorEnvelope.of(
                "NOT_FOUND", ex.getMessage(),
                ctx.requestId(), ctx.correlationId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(envelope);
    }

    private static RequestContext safeContext() {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx != null) return ctx;
        return RequestContext.of("unknown", "unknown",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                null, null, null);
    }
}
