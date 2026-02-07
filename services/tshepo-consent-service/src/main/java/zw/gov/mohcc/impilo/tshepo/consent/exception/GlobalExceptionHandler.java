package zw.gov.mohcc.impilo.tshepo.consent.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for the consent service.
 * Maps domain exceptions to standardized {@link ApiResponse} error envelopes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConsentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleConsentNotFound(ConsentNotFoundException ex) {
        log.warn("Consent not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("CONSENT_NOT_FOUND", ex.getMessage(), 404, correlationId()));
    }

    @ExceptionHandler(ConsentAlreadyRevokedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAlreadyRevoked(ConsentAlreadyRevokedException ex) {
        log.warn("Consent already revoked: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("CONSENT_ALREADY_REVOKED", ex.getMessage(), 409, correlationId()));
    }

    @ExceptionHandler(InvalidFhirConsentException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidFhir(InvalidFhirConsentException ex) {
        log.warn("Invalid FHIR Consent: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_FHIR_CONSENT", ex.getMessage(), 400, correlationId()));
    }

    @ExceptionHandler(ShareLinkNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleShareLinkNotFound(ShareLinkNotFoundException ex) {
        log.warn("Share link not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("SHARE_LINK_NOT_FOUND", ex.getMessage(), 404, correlationId()));
    }

    @ExceptionHandler(ShareLinkExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleShareLinkExpired(ShareLinkExpiredException ex) {
        log.warn("Share link expired: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.error("SHARE_LINK_EXPIRED", ex.getMessage(), 410, correlationId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", details, 400, correlationId()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Missing parameter: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("MISSING_PARAMETER", ex.getMessage(), 400, correlationId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("BAD_REQUEST", ex.getMessage(), 400, correlationId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred", 500, correlationId()));
    }

    private String correlationId() {
        TrustContext ctx = TrustContextHolder.get();
        if (ctx != null && ctx.correlationId() != null) {
            return ctx.correlationId().toString();
        }
        return UUID.randomUUID().toString();
    }
}
