package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MusheWalletServiceClient;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wallet & Payment endpoints — proxies to Mushe Wallet service with local
 * fallbacks so every pay point works without the sovereign service running.
 *
 * Covers: wallet lifecycle, balance, payments, funding sources, merchant pay.
 */
@RestController
@RequestMapping("/internal/v1/wallet")
public class WalletController {

    private static final Logger log = LoggerFactory.getLogger(WalletController.class);
    private final MusheWalletServiceClient musheClient;

    // Local fallback state
    private static final Map<String, Map<String, Object>> WALLETS = new ConcurrentHashMap<>();
    private static final List<Map<String, Object>> TRANSACTIONS = Collections.synchronizedList(new ArrayList<>());

    public WalletController(MusheWalletServiceClient musheClient) {
        this.musheClient = musheClient;
    }

    // ── Get or auto-create wallet for current user ────────────────────

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyWallet(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        String ownerId = actorId != null ? actorId : "anonymous";

        try {
            var wallet = musheClient.getWalletByOwner("PERSON", ownerId);
            if (wallet != null) {
                return ok(wallet, requestId, correlationId);
            }
        } catch (Exception e) {
            log.debug("Mushe unavailable for wallet lookup: {}", e.getMessage());
        }

        // Fallback: get or create local wallet
        Map<String, Object> wallet = WALLETS.computeIfAbsent(ownerId, id -> {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("id", "wal-" + UUID.randomUUID().toString().substring(0, 8));
            w.put("ownerType", "PERSON");
            w.put("ownerRef", id);
            w.put("displayName", "My Health Wallet");
            w.put("currency", "USD");
            w.put("balance", 250.00);
            w.put("availableBalance", 250.00);
            w.put("holdBalance", 0.00);
            w.put("status", "ACTIVE");
            w.put("createdAt", OffsetDateTime.now().toString());
            w.put("fundingSources", List.of(
                    Map.of("id", "fs-1", "sourceType", "MOBILE_MONEY", "provider", "EcoCash",
                            "accountRef", "0771****567", "status", "VERIFIED"),
                    Map.of("id", "fs-2", "sourceType", "MOBILE_MONEY", "provider", "InnBucks",
                            "accountRef", "0782****890", "status", "VERIFIED"),
                    Map.of("id", "fs-3", "sourceType", "BANK_ACCOUNT", "provider", "CBZ Bank",
                            "accountRef", "****4521", "status", "VERIFIED")
            ));
            return w;
        });

        return ok(Map.of("id", wallet.get("id"), "type", "wallet", "attributes", wallet), requestId, correlationId);
    }

    // ── Balance ───────────────────────────────────────────────────────

    @GetMapping("/me/balance")
    public ResponseEntity<Map<String, Object>> getBalance(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        String ownerId = actorId != null ? actorId : "anonymous";
        Map<String, Object> wallet = WALLETS.get(ownerId);
        double balance = wallet != null ? ((Number) wallet.getOrDefault("balance", 0)).doubleValue() : 0;
        double available = wallet != null ? ((Number) wallet.getOrDefault("availableBalance", 0)).doubleValue() : 0;

        return ok(Map.of(
                "balance", balance,
                "availableBalance", available,
                "currency", "USD",
                "lastUpdated", OffsetDateTime.now().toString()
        ), requestId, correlationId);
    }

    // ── Pay (universal payment endpoint) ──────────────────────────────

    @PostMapping("/pay")
    public ResponseEntity<Map<String, Object>> pay(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {

        String method = str(body, "method", "paymentMethod");
        double amount = num(body, "amount");
        String currency = str(body, "currency");
        String reference = str(body, "reference", "billId", "invoiceId", "orderId");
        String description = str(body, "description");
        String merchantRef = str(body, "merchantRef", "facilityId");

        if (amount <= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "amount must be positive")));
        }
        if (method == null || method.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "payment method is required")));
        }

        // Try Mushe for wallet payments
        if ("MUSHE_WALLET".equals(method)) {
            try {
                String ownerId = actorId != null ? actorId : "anonymous";
                var wallet = musheClient.getWalletByOwner("PERSON", ownerId);
                if (wallet != null && wallet.has("id")) {
                    var result = musheClient.debitWallet(
                            UUID.fromString(wallet.get("id").asText()),
                            Map.of("amount", amount, "txnType", "PAYMENT", "channel", "WEB",
                                    "reference", reference != null ? reference : "",
                                    "description", description != null ? description : "Payment"));
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                            "data", result,
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                }
            } catch (Exception e) {
                log.info("Mushe wallet payment failed, using local fallback: {}", e.getMessage());
            }

            // Local wallet fallback
            String ownerId = actorId != null ? actorId : "anonymous";
            Map<String, Object> wallet = WALLETS.get(ownerId);
            if (wallet != null) {
                double bal = ((Number) wallet.getOrDefault("balance", 0)).doubleValue();
                if (bal < amount) {
                    return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of(
                            "error", Map.of("code", "INSUFFICIENT_FUNDS",
                                    "message", "Wallet balance ($" + String.format("%.2f", bal) + ") is less than payment amount ($" + String.format("%.2f", amount) + ")")));
                }
                wallet.put("balance", bal - amount);
                wallet.put("availableBalance", bal - amount);
            }
        }

        // Create transaction record
        String txnId = "txn-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> txn = new LinkedHashMap<>();
        txn.put("id", txnId);
        txn.put("method", method);
        txn.put("amount", amount);
        txn.put("currency", currency != null ? currency : "USD");
        txn.put("reference", reference);
        txn.put("description", description);
        txn.put("merchantRef", merchantRef);
        txn.put("status", "COMPLETED");
        txn.put("createdAt", OffsetDateTime.now().toString());
        txn.put("actorId", actorId);
        TRANSACTIONS.add(txn);

        Map<String, Object> attrs = new LinkedHashMap<>(txn);
        attrs.put("receiptNumber", "RCP-" + txnId.substring(4).toUpperCase());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", Map.of("id", txnId, "type", "payment", "attributes", attrs),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    // ── Transaction history ───────────────────────────────────────────

    @GetMapping("/me/transactions")
    public ResponseEntity<Map<String, Object>> getTransactions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        String ownerId = actorId != null ? actorId : "anonymous";
        List<Map<String, Object>> userTxns = TRANSACTIONS.stream()
                .filter(t -> ownerId.equals(t.get("actorId")))
                .toList();

        return ok(userTxns, requestId, correlationId);
    }

    // ── Funding sources ───────────────────────────────────────────────

    @GetMapping("/me/funding-sources")
    public ResponseEntity<Map<String, Object>> getFundingSources(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        String ownerId = actorId != null ? actorId : "anonymous";
        Map<String, Object> wallet = WALLETS.get(ownerId);
        Object sources = wallet != null ? wallet.getOrDefault("fundingSources", List.of()) : List.of();
        return ok(sources, requestId, correlationId);
    }

    // ── Payment methods (static reference) ────────────────────────────

    @GetMapping("/payment-methods")
    public ResponseEntity<Map<String, Object>> getPaymentMethods(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        return ok(List.of(
                Map.of("id", "MUSHE_WALLET", "label", "Mushe Wallet", "icon", "wallet",
                        "description", "Pay from your Impilo digital wallet", "enabled", true),
                Map.of("id", "CASH", "label", "Cash", "icon", "banknote",
                        "description", "Pay with cash at the facility cashier", "enabled", true),
                Map.of("id", "ECOCASH", "label", "EcoCash", "icon", "smartphone",
                        "description", "Pay via EcoCash mobile money", "enabled", true),
                Map.of("id", "INNBUCKS", "label", "InnBucks", "icon", "smartphone",
                        "description", "Pay via InnBucks mobile wallet", "enabled", true),
                Map.of("id", "ONE_MONEY", "label", "OneMoney", "icon", "smartphone",
                        "description", "Pay via OneMoney mobile money", "enabled", true),
                Map.of("id", "BANK_TRANSFER", "label", "Bank Transfer / ZIPIT", "icon", "building",
                        "description", "Pay via bank transfer or ZIPIT", "enabled", true),
                Map.of("id", "VISA", "label", "Visa Card", "icon", "creditCard",
                        "description", "Pay with Visa debit or credit card", "enabled", true),
                Map.of("id", "MASTERCARD", "label", "Mastercard", "icon", "creditCard",
                        "description", "Pay with Mastercard", "enabled", true),
                Map.of("id", "INSURANCE", "label", "Medical Aid / Insurance", "icon", "shield",
                        "description", "Covered by medical aid scheme", "enabled", true),
                Map.of("id", "GOVERNMENT_SUBSIDY", "label", "Government Subsidy", "icon", "landmark",
                        "description", "Covered under government health programme", "enabled", true)
        ), requestId, correlationId);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> ok(Object data, String requestId, String correlationId) {
        return ResponseEntity.ok(Map.of(
                "data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private static String str(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }

    private static double num(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) { try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; } }
        return 0;
    }
}
