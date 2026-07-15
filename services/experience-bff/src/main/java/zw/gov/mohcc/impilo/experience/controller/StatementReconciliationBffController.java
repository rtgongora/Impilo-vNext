package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MusheWalletServiceClient;

import java.util.Map;
import java.util.Set;

/**
 * Bank→wallet cash-in reconciliation — the finance/ops surface. A back-office
 * user imports collection-account statement lines; matched lines confirm the
 * depositor's pending intent and credit the wallet, unmatched lines come back
 * for manual resolution. Finance-role gated.
 */
@RestController
@RequestMapping("/internal/v1/finance/statement-match")
public class StatementReconciliationBffController {

    private static final Logger log = LoggerFactory.getLogger(StatementReconciliationBffController.class);

    /** Actor types permitted to reconcile the collection account. */
    static final Set<String> RECON_ROLES = Set.of(
            "FINANCE", "FINANCE_ADMIN", "FACILITY_FINANCE", "PAYER_OPS", "SYSTEM", "SYSTEM_ADMIN");

    private final MusheWalletServiceClient musheClient;

    public StatementReconciliationBffController(MusheWalletServiceClient musheClient) {
        this.musheClient = musheClient;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> match(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestBody Map<String, Object> body) {
        if (actorType == null || !RECON_ROLES.contains(actorType)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", Map.of("code", "FINANCE_ROLE_REQUIRED",
                            "message", "Statement reconciliation is a finance-ops action"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        try {
            return ResponseEntity.ok(Map.of(
                    "data", musheClient.matchStatement(body),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Statement match upstream failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", Map.of("code", "WALLET_UPSTREAM_UNAVAILABLE",
                            "message", "The wallet service is temporarily unavailable"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
