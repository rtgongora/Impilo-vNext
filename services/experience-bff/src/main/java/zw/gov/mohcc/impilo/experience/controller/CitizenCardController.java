package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MusheWalletServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Unified SMART card — citizen-facing endpoints (proxies to mushe-wallet-service).
 *
 * <p>Surfaces the card's three functions to the citizen app under the authenticated
 * {@code /internal/v1/wallet/**} space: view my card(s), toggle the PHR-carry function, pay from the
 * card's offline purse (tap / scan / biometric), and raise/share a "help pay my bill" contribution
 * link. The actor→wallet binding is resolved server-side from {@code X-Actor-ID}; the BFF never
 * fabricates card or money state — it proxies and, on upstream failure, fails clean (503) or
 * propagates the upstream rejection status (e.g. 422 for a rejected offline transaction).</p>
 */
@RestController
@RequestMapping("/internal/v1/wallet")
public class CitizenCardController {

    static final String UPSTREAM_UNAVAILABLE_CODE = "WALLET_UPSTREAM_UNAVAILABLE";
    static final String UPSTREAM_UNAVAILABLE_MESSAGE =
            "The wallet service is temporarily unavailable. Please try again shortly.";

    private static final Logger log = LoggerFactory.getLogger(CitizenCardController.class);

    private final MusheWalletServiceClient musheClient;
    private final ObjectMapper objectMapper;

    public CitizenCardController(MusheWalletServiceClient musheClient, ObjectMapper objectMapper) {
        this.musheClient = musheClient;
        this.objectMapper = objectMapper;
    }

    // ── My card(s) ──────────────────────────────────────────────────────

    /** The current citizen's card(s), resolved via their wallet. */
    @GetMapping("/cards")
    public ResponseEntity<Map<String, Object>> myCards(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        UUID walletId = resolveMyWalletId(actorId);
        if (walletId == null) {
            return upstreamUnavailable("myCards", "could not resolve caller wallet", requestId, correlationId);
        }
        return proxy(() -> musheClient.getCardsByWallet(walletId), "myCards", requestId, correlationId);
    }

    /** A single card by id. */
    @GetMapping("/cards/{cardId}")
    public ResponseEntity<Map<String, Object>> getCard(
            @PathVariable UUID cardId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> musheClient.getCard(cardId), "getCard", requestId, correlationId);
    }

    // ── PHR-carry function opt-in ───────────────────────────────────────

    /** Toggle the card's PHR-carry function. Body: {@code {"enabled": true|false}}. Fail-closed upstream. */
    @PutMapping("/cards/{cardId}/phr-carry")
    public ResponseEntity<Map<String, Object>> setPhrCarry(
            @PathVariable UUID cardId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        if (body == null || body.get("enabled") == null) {
            return validation("enabled (boolean) is required", requestId, correlationId);
        }
        return proxy(() -> musheClient.setCardPhrCarry(cardId, body), "setPhrCarry", requestId, correlationId);
    }

    // ── Pay from the card's offline purse (tap / scan / biometric) ──────

    /**
     * Pay from the card's offline purse with a card-signed transaction. Body:
     * {@code {"vitoCardNumber","payload","signature"}} (the device signed the canonical payload).
     */
    @PostMapping("/cards/pay")
    public ResponseEntity<Map<String, Object>> pay(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> musheClient.redeemOfflineTransaction(body), "pay", requestId, correlationId);
    }

    /**
     * Biometric scan-to-pay. The signed payload must assert {@code authMethod=BIOMETRIC} (the device
     * biometric gated the P-256 key); mushe enforces it fail-closed and rejects otherwise (422).
     */
    @PostMapping("/cards/pay/biometric")
    public ResponseEntity<Map<String, Object>> payBiometric(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> musheClient.redeemBiometricTransaction(body), "payBiometric", requestId, correlationId);
    }

    // ── Bill contributions — "help pay my bill" links ───────────────────

    /**
     * Create a shareable contribution request for MY wallet. Body: {@code {"title","billRef",
     * "targetAmount","currency"}}. The beneficiary wallet + creator are bound to the caller server-side.
     */
    @PostMapping("/bill-contributions")
    public ResponseEntity<Map<String, Object>> createContribution(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        UUID walletId = resolveMyWalletId(actorId);
        if (walletId == null) {
            return upstreamUnavailable("createContribution", "could not resolve caller wallet",
                    requestId, correlationId);
        }
        Map<String, Object> upstream = new LinkedHashMap<>(body != null ? body : Map.of());
        upstream.put("beneficiaryWalletId", walletId.toString()); // bind to caller — ignore any client value
        upstream.put("createdBy", actorId != null ? actorId : "anonymous");
        return proxy(() -> musheClient.createBillContribution(upstream), "createContribution",
                requestId, correlationId);
    }

    /** View a contribution request by its share token (the link target) — open to whoever holds the link. */
    @GetMapping("/bill-contributions/{shareToken}")
    public ResponseEntity<Map<String, Object>> viewContribution(
            @PathVariable String shareToken,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> musheClient.getBillContribution(shareToken), "viewContribution",
                requestId, correlationId);
    }

    /** Contribute toward a shared bill. Body: {@code {"amount","contributorName","message"}}. */
    @PostMapping("/bill-contributions/{shareToken}/contribute")
    public ResponseEntity<Map<String, Object>> contribute(
            @PathVariable String shareToken,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        Map<String, Object> upstream = new LinkedHashMap<>(body != null ? body : Map.of());
        upstream.putIfAbsent("contributorRef", actorId); // attribute to the signed-in contributor
        return proxy(() -> musheClient.contributeToBill(shareToken, upstream), "contribute",
                requestId, correlationId);
    }

    /** Close my contribution request (no further contributions). */
    @PostMapping("/bill-contributions/{requestId}/close")
    public ResponseEntity<Map<String, Object>> closeContribution(
            @PathVariable("requestId") UUID contributionRequestId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> musheClient.closeBillContribution(contributionRequestId), "closeContribution",
                requestId, correlationId);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Resolve the caller's mushe wallet id from their actor id, or null if unavailable. */
    private UUID resolveMyWalletId(String actorId) {
        String ownerId = actorId != null ? actorId : "anonymous";
        try {
            JsonNode wallet = musheClient.getWalletByOwner("PERSON", ownerId);
            if (wallet != null && wallet.has("id") && !wallet.get("id").asText().isBlank()) {
                return UUID.fromString(wallet.get("id").asText());
            }
        } catch (Exception e) {
            log.debug("Mushe unavailable resolving caller wallet: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Proxy a mushe call, wrap the result in the BFF envelope, and route failures honestly: an upstream
     * HTTP status error (e.g. 422 rejected offline transaction, 404 unknown link) is propagated with its
     * status and body; any other failure fails clean as 503 (the BFF never fabricates card/money state).
     */
    private ResponseEntity<Map<String, Object>> proxy(UpstreamCall call, String operation,
                                                      String requestId, String correlationId) {
        try {
            JsonNode data = call.get();
            if (data == null) {
                return upstreamUnavailable(operation, "upstream returned null", requestId, correlationId);
            }
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException ex) {
            // Honest passthrough of an upstream rejection (422/404/…): surface the code, don't mask as 503.
            Map<String, Object> error = Map.of("code", "UPSTREAM_REJECTED",
                    "message", ex.getStatusText(), "upstreamBody", safeBody(ex));
            log.info("Card proxy {} upstream rejected: {} {}", operation, ex.getStatusCode(), safeBody(ex));
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                    "error", error,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamUnavailable(operation, "upstream exception: " + e.getClass().getSimpleName(),
                    requestId, correlationId);
        }
    }

    private JsonNode safeBody(HttpStatusCodeException ex) {
        try {
            String body = ex.getResponseBodyAsString();
            return body == null || body.isBlank() ? null : objectMapper.readTree(body);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> ok(Object data, String requestId, String correlationId) {
        return ResponseEntity.ok(Map.of(
                "data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private ResponseEntity<Map<String, Object>> validation(String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", Map.of("code", "VALIDATION", "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private ResponseEntity<Map<String, Object>> upstreamUnavailable(String operation, String reason,
                                                                    String requestId, String correlationId) {
        log.warn("CARD UPSTREAM UNAVAILABLE — operation={}, reason={} — returning 503 (BFF never fabricates state)",
                operation, reason);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", Map.of("code", UPSTREAM_UNAVAILABLE_CODE, "message", UPSTREAM_UNAVAILABLE_MESSAGE),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @FunctionalInterface
    private interface UpstreamCall {
        JsonNode get();
    }
}
