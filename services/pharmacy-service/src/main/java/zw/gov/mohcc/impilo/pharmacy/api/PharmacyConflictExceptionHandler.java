package zw.gov.mohcc.impilo.pharmacy.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Locale;
import java.util.UUID;

/**
 * Maps pharmacy state-conflict refusals to a coded 409 in the standard
 * {@link ApiResponse} error envelope (M3 F-500 — same defect family as the
 * OF-A M2 inventory fix: fail-closed holds surfaced as bodyless raw 500s).
 * Covered refusals: the OF-B15 clarification hold, invalid dispense
 * transitions, expired pickup proofs, collector-identity mismatches, the
 * SMS-reference path guard, biometric rejection, and resolve replays.
 * The fail-closed behaviour is unchanged — only the transport contract is
 * corrected. Runs at default order so the shared
 * {@code SecurityBaselineExceptionHandler} (HIGHEST_PRECEDENCE, different
 * exception types) is not shadowed.
 */
@RestControllerAdvice
public class PharmacyConflictExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PharmacyConflictExceptionHandler.class);

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleStateConflict(IllegalStateException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "State conflict";
        String code = codeFor(message.toLowerCase(Locale.ROOT));
        log.warn("State conflict [{}]: {}", code, message);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(code, message, HttpStatus.CONFLICT.value(), correlationId()));
    }

    private static String codeFor(String lower) {
        if (lower.contains("clarification")) {
            return "CLARIFICATION_PENDING";
        }
        if (lower.contains("invalid dispense transition")) {
            return "INVALID_DISPENSE_TRANSITION";
        }
        if (lower.contains("expired")) {
            return "PICKUP_PROOF_EXPIRED";
        }
        if (lower.contains("named recipient")) {
            return "COLLECTOR_IDENTITY_MISMATCH";
        }
        if (lower.contains("sms-reference")) {
            return "SMS_REFERENCE_PATH_REQUIRED";
        }
        if (lower.contains("biometric")) {
            return "BIOMETRIC_VERIFICATION_FAILED";
        }
        return "STATE_CONFLICT";
    }

    private static String correlationId() {
        TrustContext ctx = TrustContextHolder.get();
        return ctx != null && ctx.correlationId() != null
                ? ctx.correlationId().toString()
                : UUID.randomUUID().toString();
    }
}
