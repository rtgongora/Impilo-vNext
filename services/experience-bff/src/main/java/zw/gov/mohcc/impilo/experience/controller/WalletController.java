package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MusheWalletServiceClient;
import zw.gov.mohcc.impilo.experience.client.MushexServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Wallet &amp; Payment endpoints — proxies to Mushe Wallet service.
 *
 * <p>When the upstream Mushe Wallet service is reachable, requests are forwarded and the upstream
 * response is returned unchanged. When the upstream call fails (exception or empty response), the
 * BFF <strong>fails clean</strong> — it returns {@code 503 Service Unavailable} with the stable
 * error code {@code WALLET_UPSTREAM_UNAVAILABLE} (and {@code 501} for payment methods not wired to
 * a production rail). It NEVER fabricates wallet state (balances, transactions, funding sources)
 * from BFF memory: a stateless composition layer must not be a source of truth for financial data.
 * (The former {@code impilo.wallet.allow-local-fallback} in-memory fabrication was removed —
 * mushe-wallet-service is the sole owner; the BFF only proxies.)</p>
 *
 * <p>Covers: wallet lifecycle, balance, payments, funding sources, merchant pay.</p>
 */
@RestController
@RequestMapping("/internal/v1/wallet")
public class WalletController {

    static final String UPSTREAM_UNAVAILABLE_CODE = "WALLET_UPSTREAM_UNAVAILABLE";
    static final String UPSTREAM_UNAVAILABLE_MESSAGE =
            "The wallet service is temporarily unavailable. Please try again shortly.";

    private static final Logger log = LoggerFactory.getLogger(WalletController.class);
    private final MusheWalletServiceClient musheClient;
    private final MushexServiceClient mushexClient;
    private final ObjectMapper objectMapper;

    public WalletController(MusheWalletServiceClient musheClient,
                            MushexServiceClient mushexClient,
                            ObjectMapper objectMapper) {
        this.musheClient = musheClient;
        this.mushexClient = mushexClient;
        this.objectMapper = objectMapper;
    }

    // ── Get or auto-create wallet for current user ────────────────────

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyWallet(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        String ownerId = actorId != null ? actorId : "anonymous";

        WalletLookup lookup = lookupWalletByOwner(ownerId);
        if (lookup.present()) {
            return ok(lookup.wallet(), requestId, correlationId);
        }

        // A stateless BFF must never fabricate wallet/financial state: when mushe-wallet-service is
        // unavailable, fail clean (the caller retries / shows a degraded state) rather than inventing
        // a balance. (Former allow-local-fallback in-memory wallet removed — see class javadoc.)
        return walletUpstreamUnavailable("getMyWallet", lookup.failureReason(), requestId, correlationId);
    }

    // ── Balance ───────────────────────────────────────────────────────

    @GetMapping("/me/balance")
    public ResponseEntity<Map<String, Object>> getBalance(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        String ownerId = actorId != null ? actorId : "anonymous";
        WalletLookup lookup = lookupWalletByOwner(ownerId);
        String failureReason = lookup.failureReason();

        if (lookup.hasUsableId()) {
            try {
                JsonNode balance = musheClient.getBalance(lookup.id());
                if (balance != null) {
                    return ok(balance, requestId, correlationId);
                }
                failureReason = "upstream balance call returned null";
            } catch (Exception e) {
                log.debug("Mushe unavailable for balance lookup: {}", e.getMessage());
                failureReason = "upstream balance exception: " + e.getClass().getSimpleName();
            }
        } else if (lookup.present()) {
            failureReason = "upstream wallet missing id";
        }

        // Never fabricate a balance from BFF memory — fail clean when the sovereign wallet is unavailable.
        return walletUpstreamUnavailable("getBalance", failureReason, requestId, correlationId);
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
            return validationFailure("VALIDATION", "amount must be positive", requestId, correlationId);
        }
        if (method == null || method.isBlank()) {
            return validationFailure("VALIDATION", "payment method is required", requestId, correlationId);
        }

        // Try Mushe for wallet payments
        if ("MUSHE_WALLET".equals(method)) {
            // Pay-confirm seam: when the reference is a known MusheX payment intent
            // (marketplace/fee flows pass reference = intentId), settle THROUGH the
            // money SoR — MusheX debits the payer's CPID-keyed wallet and records the
            // payment, so the intent reaches PAID from the real debit. Only when the
            // reference is NOT an intent do we fall through to the direct debit
            // (merchant/ad-hoc payments). If intent settlement fails we fail clean —
            // never fall back to a bare debit that takes money without paying the order.
            if (reference != null && !reference.isBlank()) {
                Boolean isIntent = lookupIntentExists(reference);
                if (Boolean.TRUE.equals(isIntent)) {
                    try {
                        ResponseEntity<String> paid = mushexClient.payIntentFromWallet(reference);
                        JsonNode body2 = objectMapper.readTree(paid.getBody() != null ? paid.getBody() : "{}");
                        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                                "data", body2.has("data") ? body2.get("data") : body2,
                                "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                                        "settlement", "INTENT_WALLET_PAYMENT")));
                    } catch (Exception e) {
                        log.warn("Intent wallet payment failed for {}: {}", reference, e.getMessage());
                        return walletUpstreamUnavailable("pay[MUSHE_WALLET:intent]",
                                "mushex pay-from-wallet failed: " + e.getMessage(), requestId, correlationId);
                    }
                }
                if (isIntent == null) {
                    // MusheX unreachable — cannot tell whether the reference is an intent.
                    // Debiting anyway could take money without settling the order: fail clean.
                    return walletUpstreamUnavailable("pay[MUSHE_WALLET:intent-lookup]",
                            "mushex unreachable during intent lookup", requestId, correlationId);
                }
            }
            String upstreamFailureReason = null;
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
                upstreamFailureReason = "upstream returned no wallet";
            } catch (Exception e) {
                log.info("Mushe wallet payment failed: {}", e.getMessage());
                upstreamFailureReason = "upstream exception: " + e.getClass().getSimpleName();
            }

            // Never debit/fabricate wallet state from BFF memory — fail clean when mushe-wallet is down.
            return walletUpstreamUnavailable("pay[MUSHE_WALLET]", upstreamFailureReason,
                    requestId, correlationId);
        }

        // Non-MUSHE_WALLET methods are not wired to a production payment rail — fail closed instead
        // of synthesizing a successful financial transaction.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of(
                "error", Map.of(
                        "code", "PAYMENT_METHOD_UNAVAILABLE",
                        "message", "Requested payment method is not yet wired to a production payment rail"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    // ── Transaction history ───────────────────────────────────────────

    @GetMapping("/me/transactions")
    public ResponseEntity<Map<String, Object>> getTransactions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        String ownerId = actorId != null ? actorId : "anonymous";
        WalletLookup lookup = lookupWalletByOwner(ownerId);
        String failureReason = lookup.failureReason();

        if (lookup.hasUsableId()) {
            try {
                JsonNode transactions = musheClient.getTransactions(
                        lookup.id(), DEFAULT_TXN_PAGE, DEFAULT_TXN_SIZE);
                if (transactions != null) {
                    return ok(transactions, requestId, correlationId);
                }
                failureReason = "upstream transactions call returned null";
            } catch (Exception e) {
                log.debug("Mushe unavailable for transactions lookup: {}", e.getMessage());
                failureReason = "upstream transactions exception: " + e.getClass().getSimpleName();
            }
        } else if (lookup.present()) {
            failureReason = "upstream wallet missing id";
        }

        // Never serve a fabricated transaction history from BFF memory — fail clean when mushe-wallet is down.
        return walletUpstreamUnavailable("getTransactions", failureReason, requestId, correlationId);
    }

    // ── Funding sources ───────────────────────────────────────────────

    @GetMapping("/me/funding-sources")
    public ResponseEntity<Map<String, Object>> getFundingSources(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        String ownerId = actorId != null ? actorId : "anonymous";
        WalletLookup lookup = lookupWalletByOwner(ownerId);
        String failureReason = lookup.failureReason();

        if (lookup.hasUsableId()) {
            try {
                JsonNode sources = musheClient.listFundingSources(lookup.id());
                if (sources != null) {
                    return ok(sources, requestId, correlationId);
                }
                failureReason = "upstream funding-sources call returned null";
            } catch (Exception e) {
                log.debug("Mushe unavailable for funding-sources lookup: {}", e.getMessage());
                failureReason = "upstream funding-sources exception: " + e.getClass().getSimpleName();
            }
        } else if (lookup.present()) {
            failureReason = "upstream wallet missing id";
        }

        // Never serve fabricated funding sources from BFF memory — fail clean when mushe-wallet is down.
        return walletUpstreamUnavailable("getFundingSources", failureReason, requestId, correlationId);
    }

    // ── Payment methods (static reference) ────────────────────────────

    @GetMapping("/payment-methods")
    public ResponseEntity<Map<String, Object>> getPaymentMethods(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        // Enablement is driven by REAL MusheX adapter readiness, not a hardcoded
        // 'true' — the citizen only ever sees a method that can actually take money.
        // External methods (EcoCash/OneMoney/InnBucks/bank/cards) ride the Paynow
        // aggregator = the CARD_GATEWAY rail; they light up together when it is
        // READY_LIVE. The internal wallet and coverage methods are always shown.
        boolean aggregatorLive = isRailLive("CARD_GATEWAY");
        String extNote = aggregatorLive ? null : "Not yet available — coming soon";

        return ok(List.of(
                method("MUSHE_WALLET", "Mushe Wallet", "wallet",
                        "Pay from your Impilo digital wallet", true, null),
                method("CASH", "Cash", "banknote",
                        "Pay with cash at the facility cashier", true, null),
                method("ECOCASH", "EcoCash", "smartphone",
                        "Pay via EcoCash mobile money", aggregatorLive, extNote),
                method("INNBUCKS", "InnBucks", "smartphone",
                        "Pay via InnBucks mobile wallet", aggregatorLive, extNote),
                method("ONE_MONEY", "OneMoney", "smartphone",
                        "Pay via OneMoney mobile money", aggregatorLive, extNote),
                method("BANK_TRANSFER", "Bank Transfer / ZIPIT", "building",
                        "Pay via bank transfer or ZIPIT", aggregatorLive, extNote),
                method("VISA", "Visa Card", "creditCard",
                        "Pay with Visa debit or credit card", aggregatorLive, extNote),
                method("MASTERCARD", "Mastercard", "creditCard",
                        "Pay with Mastercard", aggregatorLive, extNote),
                method("INSURANCE", "Medical Aid / Insurance", "shield",
                        "Covered by medical aid scheme", true, null),
                method("GOVERNMENT_SUBSIDY", "Government Subsidy", "landmark",
                        "Covered under government health programme", true, null)
        ), requestId, correlationId);
    }

    private static Map<String, Object> method(String id, String label, String icon,
                                              String description, boolean enabled, String unavailableReason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("label", label);
        m.put("icon", icon);
        m.put("description", description);
        m.put("enabled", enabled);
        if (unavailableReason != null) {
            m.put("unavailableReason", unavailableReason);
        }
        return m;
    }

    /** True when MusheX reports the given rail as READY_LIVE; false on any doubt (fail-closed). */
    private boolean isRailLive(String railType) {
        try {
            ResponseEntity<String> resp = mushexClient.platformAdapterReadiness();
            if (resp.getBody() == null) return false;
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode rows = root.has("data") ? root.get("data") : root;
            if (rows != null && rows.isArray()) {
                for (JsonNode row : rows) {
                    if (railType.equals(row.path("adapterType").asText())
                            && "READY_LIVE".equals(row.path("status").asText())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.debug("Adapter readiness lookup failed, treating external methods as unavailable: {}",
                    e.getMessage());
            return false;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /** Default page index for the upstream {@code getTransactions} call. */
    private static final int DEFAULT_TXN_PAGE = 0;
    /** Default page size for the upstream {@code getTransactions} call. */
    private static final int DEFAULT_TXN_SIZE = 50;

    /**
     * Resolves the upstream Mushe wallet for the given owner. Captures both
     * "upstream threw" and "upstream returned null" as failure modes so the
     * caller can route consistently through {@link #walletUpstreamUnavailable}.
     *
     * <p>Shared between {@code /me}, {@code /me/balance},
     * {@code /me/transactions}, and {@code /me/funding-sources} to avoid
     * duplicating the upstream try/catch/null-check four times
     * (audit gap {@code G-5.1}).</p>
     */
    /**
     * True/false when MusheX answered whether the reference is a payment intent;
     * null when MusheX was unreachable (the caller must fail clean, not guess).
     */
    private Boolean lookupIntentExists(String reference) {
        try {
            ResponseEntity<String> resp = mushexClient.getPaymentIntent(reference);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException.NotFound nf) {
            return false;
        } catch (Exception e) {
            log.warn("MusheX unreachable during intent lookup for {}: {}", reference, e.getMessage());
            return null;
        }
    }

    private WalletLookup lookupWalletByOwner(String ownerId) {
        try {
            JsonNode wallet = musheClient.getWalletByOwner("PERSON", ownerId);
            if (wallet != null) {
                return new WalletLookup(wallet, null);
            }
            return new WalletLookup(null, "upstream returned no wallet");
        } catch (Exception e) {
            log.debug("Mushe unavailable for wallet lookup: {}", e.getMessage());
            return new WalletLookup(null, "upstream exception: " + e.getClass().getSimpleName());
        }
    }

    /** Result of a {@link #lookupWalletByOwner(String)} call. */
    private record WalletLookup(JsonNode wallet, String failureReason) {
        /** {@code true} when upstream returned a (possibly malformed) wallet. */
        boolean present() {
            return wallet != null;
        }

        /**
         * {@code true} when the upstream wallet payload exposes a non-blank
         * {@code id} field that can be parsed into a {@link UUID}. Callers
         * use this to decide whether the second upstream call (balance /
         * transactions / funding-sources) can be attempted.
         */
        boolean hasUsableId() {
            return wallet != null && wallet.has("id") && !wallet.get("id").asText().isBlank();
        }

        /**
         * Wallet id parsed as a {@link UUID}. Only call after
         * {@link #hasUsableId()} returns {@code true}; a malformed id will
         * surface as an {@link IllegalArgumentException} which the caller
         * is expected to catch (matching the {@code Exception} handler in
         * each endpoint).
         */
        UUID id() {
            return UUID.fromString(wallet.get("id").asText());
        }
    }

    private ResponseEntity<Map<String, Object>> ok(Object data, String requestId, String correlationId) {
        return ResponseEntity.ok(Map.of(
                "data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    /**
     * Doctrine-correct 503 response when the upstream Mushe Wallet service is
     * unavailable. Returns the stable error code {@link #UPSTREAM_UNAVAILABLE_CODE}
     * so callers can distinguish "wallet service is down" from "this owner has no
     * wallet" without parsing free-text messages. The BFF never substitutes
     * fabricated financial state for the unavailable sovereign — it fails clean.
     */
    private ResponseEntity<Map<String, Object>> walletUpstreamUnavailable(String operation,
                                                                          String reason,
                                                                          String requestId,
                                                                          String correlationId) {
        log.warn("WALLET UPSTREAM UNAVAILABLE — operation={}, reason={}, requestId={}, correlationId={} — "
                        + "returning 503 (BFF never fabricates wallet state)",
                operation, reason, requestId, correlationId);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", Map.of(
                        "code", UPSTREAM_UNAVAILABLE_CODE,
                        "message", UPSTREAM_UNAVAILABLE_MESSAGE),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private ResponseEntity<Map<String, Object>> validationFailure(String code,
                                                                  String message,
                                                                  String requestId,
                                                                  String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", Map.of("code", code, "message", message),
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
