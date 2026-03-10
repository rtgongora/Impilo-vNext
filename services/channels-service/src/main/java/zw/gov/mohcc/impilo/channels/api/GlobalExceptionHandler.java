package zw.gov.mohcc.impilo.channels.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.channels.service.SessionService;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorCodes;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorEnvelope> handleMissingHeader(MissingRequestHeaderException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCodes.MISSING_REQUIRED_HEADER,
                "Missing required header: " + ex.getHeaderName());
    }

    @ExceptionHandler(SessionService.SessionNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleNotFound(SessionService.SessionNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorEnvelope> handleIllegalState(IllegalStateException ex) {
        return buildResponse(HttpStatus.CONFLICT, ErrorCodes.VALIDATION_FAILED, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorEnvelope> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, ex.getMessage());
    }

    private ResponseEntity<ErrorEnvelope> buildResponse(HttpStatus status, String code, String message) {
        RequestContext ctx = RequestContextHolder.get();
        String requestId = ctx != null ? ctx.requestId() : "unknown";
        String correlationId = ctx != null ? ctx.correlationId() : "unknown";
        ErrorEnvelope envelope = ErrorEnvelope.of(code, message, requestId, correlationId);
        return ResponseEntity.status(status).body(envelope);
    }
}
