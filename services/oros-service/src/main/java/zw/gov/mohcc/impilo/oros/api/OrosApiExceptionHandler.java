package zw.gov.mohcc.impilo.oros.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.oros.core.OrosDomainException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * Maps {@link OrosDomainException} to the standard {@link ApiResponse} error envelope with the
 * domain's intended HTTP status + code. Lets governed OROS rejections (e.g. the TM-B7
 * duplicate-teleconsult-order 409) reach the client — and the BFF A0 passthrough — with a real
 * code instead of an opaque Spring error body.
 */
@RestControllerAdvice
public class OrosApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OrosApiExceptionHandler.class);

    @ExceptionHandler(OrosDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(OrosDomainException ex) {
        String correlationId = safeCorrelationId();
        log.info("OROS domain rejection: code={} status={} msg={}", ex.getCode(), ex.getStatus(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(ex.getCode(), ex.getMessage(), ex.getStatus(), correlationId));
    }

    private String safeCorrelationId() {
        TrustContext ctx = TrustContextHolder.get();
        return ctx != null && ctx.correlationId() != null ? ctx.correlationId().toString() : null;
    }
}
