package zw.gov.mohcc.impilo.pct.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.pct.core.PctDomainException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * Global mapping of {@link PctDomainException} to the standard {@link ApiResponse}
 * error envelope with the domain's intended HTTP status.
 *
 * <p>Controllers that catch {@code PctDomainException} locally (ReferralController,
 * TelehealthController, JourneyController via their {@code execute(...)} wrappers)
 * return before propagation, so this advice never fires for them — it is a no-conflict
 * backstop for controllers without a local catch (notably {@code TelemedicineController},
 * the active teleconsult spine). Without it, domain rejections such as the
 * completion-note 400 or the TM-B1 {@code ILLEGAL_TRANSITION}/{@code CONSENT_REQUIRED_MISSING}/
 * {@code PROVIDER_NOT_AUTHORIZED} 4xx would surface as an unhandled 500.
 */
@RestControllerAdvice
public class PctApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PctApiExceptionHandler.class);

    @ExceptionHandler(PctDomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(PctDomainException ex) {
        String correlationId = safeCorrelationId();
        log.info("PCT domain rejection: code={} status={} msg={}", ex.getCode(), ex.getStatus(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(ex.getCode(), ex.getMessage(), ex.getStatus(), correlationId));
    }

    /**
     * Optimistic-concurrency guard (TM-B6): a concurrent edit of the same referral (double-accept,
     * provider-complete vs timer-expire) that Hibernate's {@code @Version} check detected. Surfaced
     * as a clean 409 STALE_WRITE so the experience layer prompts a reload-and-retry instead of
     * silently losing an update or returning a 500. Covers Spring's OptimisticLockingFailureException,
     * which wraps JPA's ObjectOptimisticLockingFailureException / OptimisticLockException.
     */
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleStaleWrite(
            org.springframework.dao.OptimisticLockingFailureException ex) {
        String correlationId = safeCorrelationId();
        log.info("PCT stale write (optimistic lock): {}", ex.getMessage());
        return ResponseEntity.status(409).body(ApiResponse.error("STALE_WRITE",
                "This consultation was changed by someone else. Reload the latest version and retry.",
                409, correlationId));
    }

    private String safeCorrelationId() {
        TrustContext ctx = TrustContextHolder.get();
        return ctx != null && ctx.correlationId() != null ? ctx.correlationId().toString() : null;
    }
}
