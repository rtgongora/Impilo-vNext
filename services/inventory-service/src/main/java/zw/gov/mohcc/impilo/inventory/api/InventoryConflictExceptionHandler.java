package zw.gov.mohcc.impilo.inventory.api;

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
 * Maps state-conflict refusals to a coded 409 in the standard
 * {@link ApiResponse} error envelope (BUG-2, Wave OF-A M2 proof: the DURA
 * over-reserve fail-close in {@code ReservationServiceImpl.reserve} surfaced
 * as a raw 500). {@code INSUFFICIENT_STOCK} when the refusal is a stock
 * shortfall, {@code RESERVATION_CONFLICT} otherwise. Runs at default order so
 * the shared {@code SecurityBaselineExceptionHandler}
 * (HIGHEST_PRECEDENCE, different exception types) is not shadowed.
 */
@RestControllerAdvice
public class InventoryConflictExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(InventoryConflictExceptionHandler.class);

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleStateConflict(IllegalStateException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "State conflict";
        String code = message.toLowerCase(Locale.ROOT).contains("stock")
                ? "INSUFFICIENT_STOCK" : "RESERVATION_CONFLICT";
        log.warn("State conflict [{}]: {}", code, message);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(code, message, HttpStatus.CONFLICT.value(), correlationId()));
    }

    private static String correlationId() {
        TrustContext ctx = TrustContextHolder.get();
        return ctx != null && ctx.correlationId() != null
                ? ctx.correlationId().toString()
                : UUID.randomUUID().toString();
    }
}
