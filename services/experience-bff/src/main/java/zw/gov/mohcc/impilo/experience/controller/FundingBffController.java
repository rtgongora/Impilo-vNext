package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MusheWalletServiceClient;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;

/**
 * Wallet funding routes — the citizen cash-in surface. These paths were called
 * by the shell ({@code useDeposit}/{@code useCashDeposit}) but served by
 * nothing: the deposit journey was dead at the BFF.
 *
 * <p>Honest contract: an external-source deposit returns a PENDING deposit
 * intent with the reference code the citizen must quote (ZIPIT narrative /
 * provider reference) — the balance rises only when the money's arrival is
 * confirmed upstream. Cash deposits are cashier-gated and immediate (money in
 * hand). All citizen routes are self-wallet-only.</p>
 */
@RestController
@RequestMapping("/internal/v1/funding")
public class FundingBffController {

    private static final Logger log = LoggerFactory.getLogger(FundingBffController.class);

    /** Actor types that may take physical cash over a counter. */
    /**
     * Cashier actions on funding.
     *
     * <p>Was {@code {CASHIER, FACILITY_FINANCE, FINANCE_ADMIN, SYSTEM, SYSTEM_ADMIN}}; only SYSTEM
     * is emittable, so no cashier could act.</p>
     */
    static final ActorTypeGuard.Duty ACT_AS_CASHIER = new ActorTypeGuard.Duty(
            "acting as cashier on funding",
            ActorTypeGuard.BACK_OFFICE_WRITERS,
            java.util.Set.of("CASHIER", "FACILITY_FINANCE", "FINANCE_ADMIN", "SYSTEM_ADMIN"));

    private final MusheWalletServiceClient musheClient;

    public FundingBffController(MusheWalletServiceClient musheClient) {
        this.musheClient = musheClient;
    }

    @PostMapping("/{walletId}/sources")
    public ResponseEntity<Map<String, Object>> addFundingSource(
            @PathVariable UUID walletId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> denial = requireOwnWallet(walletId, actorId, requestId, correlationId);
        if (denial != null) return denial;
        return created(musheClient.addFundingSource(walletId, body), requestId, correlationId);
    }

    @GetMapping("/{walletId}/sources")
    public ResponseEntity<Map<String, Object>> listFundingSources(
            @PathVariable UUID walletId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        ResponseEntity<Map<String, Object>> denial = requireOwnWallet(walletId, actorId, requestId, correlationId);
        if (denial != null) return denial;
        return ok(musheClient.listFundingSources(walletId), requestId, correlationId);
    }

    /**
     * Requests an external-source deposit. Returns the PENDING intent — the
     * citizen quotes its reference code with their transfer; the balance rises
     * only on confirmed arrival.
     */
    @PostMapping("/{walletId}/deposit")
    public ResponseEntity<Map<String, Object>> requestDeposit(
            @PathVariable UUID walletId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> denial = requireOwnWallet(walletId, actorId, requestId, correlationId);
        if (denial != null) return denial;
        return created(musheClient.deposit(walletId, body), requestId, correlationId);
    }

    @GetMapping("/{walletId}/deposits")
    public ResponseEntity<Map<String, Object>> listDeposits(
            @PathVariable UUID walletId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        ResponseEntity<Map<String, Object>> denial = requireOwnWallet(walletId, actorId, requestId, correlationId);
        if (denial != null) return denial;
        return ok(musheClient.listDeposits(walletId), requestId, correlationId);
    }

    /**
     * Cancel a still-pending deposit the citizen no longer intends to fund.
     * Self-wallet-only: the wallet path is ownership-checked, and deposit ids are
     * only ever obtained from the caller's own deposit list. Confirmation is NOT
     * exposed here — money arrival is confirmed by ops/system (statement recon /
     * provider callback), never self-served.
     */
    @PostMapping("/{walletId}/deposits/{depositId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelDeposit(
            @PathVariable UUID walletId,
            @PathVariable UUID depositId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        ResponseEntity<Map<String, Object>> denial = requireOwnWallet(walletId, actorId, requestId, correlationId);
        if (denial != null) return denial;
        return ok(musheClient.cancelDeposit(depositId), requestId, correlationId);
    }

    /** Cash over the counter — cashier-gated; the citizen never self-serves cash-in. */
    @PostMapping("/{walletId}/cash-deposit")
    public ResponseEntity<Map<String, Object>> cashDeposit(
            @PathVariable UUID walletId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestBody Map<String, Object> body) {
        if (!ActorTypeGuard.permits(actorType, ACT_AS_CASHIER)) {
            return error(HttpStatus.FORBIDDEN, "CASHIER_ROLE_REQUIRED",
                    "Cash deposits are taken by a facility cashier", requestId, correlationId);
        }
        return created(musheClient.cashDeposit(walletId, body), requestId, correlationId);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * Citizen funding routes are self-wallet-only: the wallet's ownerRef must be
     * the caller. Fails clean (404/403) — never operates on someone else's wallet.
     */
    private ResponseEntity<Map<String, Object>> requireOwnWallet(UUID walletId, String actorId,
                                                                 String requestId, String correlationId) {
        if (actorId == null || actorId.isBlank()) {
            return error(HttpStatus.FORBIDDEN, "ACTOR_REQUIRED",
                    "Funding operations require an authenticated actor", requestId, correlationId);
        }
        try {
            JsonNode wallet = musheClient.getWallet(walletId);
            if (wallet == null) {
                return error(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND",
                        "Wallet not found", requestId, correlationId);
            }
            String ownerRef = wallet.path("ownerRef").asText("");
            if (!actorId.equals(ownerRef)) {
                log.warn("Funding denied: wallet {} owner {} != actor {}", walletId, ownerRef, actorId);
                return error(HttpStatus.FORBIDDEN, "NOT_WALLET_OWNER",
                        "You can only fund your own wallet", requestId, correlationId);
            }
            return null;
        } catch (Exception e) {
            log.warn("Wallet ownership check failed for {}: {}", walletId, e.getMessage());
            return error(HttpStatus.SERVICE_UNAVAILABLE, "WALLET_UPSTREAM_UNAVAILABLE",
                    "The wallet service is temporarily unavailable", requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> ok(Object data, String requestId, String correlationId) {
        return ResponseEntity.ok(envelope(data, requestId, correlationId));
    }

    private ResponseEntity<Map<String, Object>> created(Object data, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(envelope(data, requestId, correlationId));
    }

    private Map<String, Object> envelope(Object data, String requestId, String correlationId) {
        return Map.of("data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId));
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message,
                                                      String requestId, String correlationId) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
