package zw.gov.mohcc.impilo.wallet.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.wallet.card.OfflineTransactionService;
import zw.gov.mohcc.impilo.wallet.persistence.entity.TransactionEntity;

import java.util.Map;

/**
 * Reconcile a card-signed offline (stored-value) transaction into the ledger (unified SMART card,
 * Phase 3). Backs tap-to-pay / scan-to-pay done while the phone/reader was offline: the card presents
 * a signed transaction that mushe verifies against the card's device/SE public key and applies
 * idempotently. The ledger is the money SoR; the on-card purse is a reconciled float.
 */
@RestController
@RequestMapping("/internal/v1/wallets/offline-transactions")
public class OfflineTransactionController {

    private final OfflineTransactionService offlineTransactionService;

    public OfflineTransactionController(OfflineTransactionService offlineTransactionService) {
        this.offlineTransactionService = offlineTransactionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TransactionEntity>> redeem(
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            TransactionEntity txn = offlineTransactionService.redeem(
                    str(body, "vitoCardNumber"), str(body, "payload"), str(body, "signature"));
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(txn, correlationId));
        } catch (OfflineTransactionService.OfflineTransactionRejected e) {
            // Honest 422 — the offline transaction failed verification/policy (bad signature, replay,
            // stale counter, unlinked/blocked card). Never a fabricated success.
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error("OFFLINE_TXN_REJECTED", e.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY.value(), correlationId));
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? null : v.toString();
    }
}
