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
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Unified SMART card — citizen-facing endpoints (proxies to mushe-wallet-service / VITO).
 *
 * <p>Surfaces the card's three functions to the citizen app under the authenticated
 * {@code /internal/v1/wallet/**} space: view my card(s), toggle the PHR-carry function, pay from the
 * card's offline purse (tap / scan / biometric), enroll a device key with VITO, and raise/share a
 * "help pay my bill" contribution link. The actor→wallet binding is resolved server-side from
 * {@code X-Actor-ID}; the BFF never fabricates card or money state — it proxies and, on upstream
 * failure, fails clean (503) or propagates the upstream rejection status.</p>
 */
@RestController
@RequestMapping("/internal/v1/wallet")
public class CitizenCardController {

    static final String UPSTREAM_UNAVAILABLE_CODE = "WALLET_UPSTREAM_UNAVAILABLE";
    static final String UPSTREAM_UNAVAILABLE_MESSAGE =
            "The wallet service is temporarily unavailable. Please try again shortly.";

    private static final Logger log = LoggerFactory.getLogger(CitizenCardController.class);

    private final MusheWalletServiceClient musheClient;
    private final VitoServiceClient vitoClient;
    private final ObjectMapper objectMapper;

    public CitizenCardController(MusheWalletServiceClient musheClient,
                                 VitoServiceClient vitoClient,
                                 ObjectMapper objectMapper) {
        this.musheClient = musheClient;
        this.vitoClient = vitoClient;
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

    // ── Report lost / stolen (CJ8) ──────────────────────────────────────

    /**
     * Citizen self-service "report my card lost/stolen" (Identity Journey Doctrine, CJ8). The
     * citizen never supplies a card id — the BFF resolves their own active SMART card from the
     * authenticated actor (X-Actor-ID == Health ID), revokes it with the stated reason so it can
     * no longer authenticate, and optionally raises a replacement request. Only LOST/STOLEN are
     * accepted here (operational reasons like DAMAGED/EXPIRED are an operator concern).
     *
     * <p>Body: {@code {"reason":"LOST|STOLEN","requestReplacement":true}} (both optional; reason
     * defaults to LOST). Returns what happened, without ever exposing another person's card.</p>
     */
    @PostMapping("/cards/report-lost")
    public ResponseEntity<Map<String, Object>> reportLost(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        if (actorId == null || actorId.isBlank()) {
            return validation("X-Actor-ID is required to report a lost card", requestId, correlationId);
        }
        String reason = "STOLEN".equalsIgnoreCase(str(body == null ? null : body.get("reason")))
                ? "STOLEN" : "LOST";
        boolean requestReplacement = body != null && Boolean.TRUE.equals(body.get("requestReplacement"));

        try {
            JsonNode active = vitoClient.getActiveCard(actorId);
            Long cardId = active == null ? null
                    : (active.path("id").isMissingNode() || active.path("id").isNull()
                        ? null : active.path("id").asLong());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reason", reason);
            if (cardId == null) {
                // Nothing active to revoke — still let the citizen request a card if they asked.
                data.put("status", "NO_ACTIVE_CARD");
                data.put("message", "No active card was found on your record to report. "
                        + "You can request a new card.");
            } else {
                vitoClient.revokeCard(cardId, reason);
                data.put("status", "REPORTED");
                data.put("revokedCardId", cardId);
                data.put("message", reason.equals("STOLEN")
                        ? "Your card has been reported stolen and can no longer be used."
                        : "Your card has been reported lost and can no longer be used.");
            }

            data.put("replacementRequested", requestReplacement);
            if (requestReplacement) {
                JsonNode replacement = vitoClient.requestCard(actorId, "PHYSICAL");
                data.put("replacement", replacement);
            }
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            log.warn("report-lost upstream rejected: {}", e.getStatusCode());
            return upstreamUnavailable("reportLost", "vito rejected", requestId, correlationId);
        } catch (Exception e) {
            return upstreamUnavailable("reportLost", e.getMessage(), requestId, correlationId);
        }
    }

    // ── Fresh physical card request (through PROOFING, B3) ──────────────

    /**
     * Citizen self-service request for a FRESH physical SMART card. Unlike the lost/stolen
     * replacement fast path ({@code report-lost}), a fresh card is submitted on the governed
     * PORTAL issuance channel, which auto-routes to PROOFING — the citizen must be identity-proofed
     * (in-person appointment) before the card is produced. Health ID is taken from {@code X-Actor-ID}
     * (never the body). Returns the created issuance request (its id + PROOFING state) so the citizen
     * can track it.
     */
    @PostMapping("/cards/request-physical")
    public ResponseEntity<Map<String, Object>> requestPhysicalCard(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        UUID healthId = uuidOrNull(actorId);
        if (healthId == null) {
            return validation("X-Actor-ID must be the citizen Health ID UUID", requestId, correlationId);
        }
        // REPLACEMENT only if the citizen explicitly says so; a first card is NEW.
        String type = "REPLACEMENT".equalsIgnoreCase(str(body == null ? null : body.get("type")))
                ? "REPLACEMENT" : "NEW";
        try {
            JsonNode req = vitoClient.submitPortalIssuance(healthId.toString(), type);
            if (req == null) {
                return upstreamUnavailable("requestPhysicalCard", "vito returned null", requestId, correlationId);
            }
            return ok(req, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            log.info("requestPhysicalCard upstream rejected: {}", e.getStatusCode());
            return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                    "error", Map.of("code", "UPSTREAM_REJECTED", "message", e.getStatusText()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamUnavailable("requestPhysicalCard", e.getMessage(), requestId, correlationId);
        }
    }

    /** Track a fresh-card issuance request's state (PROOFING → APPROVED → ISSUED → DELIVERED). */
    @GetMapping("/cards/issuance/{requestId}")
    public ResponseEntity<Map<String, Object>> issuanceStatus(
            @PathVariable("requestId") long issuanceRequestId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> vitoClient.getIssuanceRequest(issuanceRequestId), "issuanceStatus",
                requestId, correlationId);
    }

    // ── Present-to-verify (card sign-in / POS resolve, B5) ──────────────

    /**
     * Resolve a PRESENTED card (scanned QR, printed assertion, or entered number) to its
     * identity + status through the B0 resolve seam — the primitive behind card sign-in and
     * a POS "who is this card" check. Body: exactly one of {@code cardNumber|qrToken|cardAssertion}.
     * Fail-closed: an unresolved/unverifiable presentation → 404, never a fabricated identity.
     */
    @PostMapping("/cards/resolve-presentation")
    public ResponseEntity<Map<String, Object>> resolvePresentation(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        if (body == null || (str(body.get("cardNumber")) == null
                && str(body.get("qrToken")) == null && str(body.get("cardAssertion")) == null)) {
            return validation("one of cardNumber, qrToken, cardAssertion is required",
                    requestId, correlationId);
        }
        return proxy(() -> vitoClient.resolveCard(
                        str(body.get("cardNumber")), str(body.get("qrToken")), str(body.get("cardAssertion"))),
                "resolvePresentation", requestId, correlationId);
    }

    // ── Device enrollment (VIRTUAL card key → VITO → mushe link) ────────

    /**
     * Enroll this device's P-256 public key as the citizen's VIRTUAL SMART card credential,
     * then link the caller's Mushe money card so offline/biometric pay can verify signatures.
     * Body: {@code {"cardId":"<mushe card uuid>","publicKey":"-----BEGIN PUBLIC KEY-----..."}}.
     * Health ID is taken from {@code X-Actor-ID} (never from the client body).
     */
    @PostMapping("/cards/enroll-device")
    public ResponseEntity<Map<String, Object>> enrollDevice(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        if (body == null) {
            return validation("cardId and publicKey are required", requestId, correlationId);
        }
        String publicKey = str(body.get("publicKey"));
        UUID musheCardId = uuidOrNull(body.get("cardId"));
        if (publicKey == null || musheCardId == null) {
            return validation("cardId and publicKey are required", requestId, correlationId);
        }
        UUID healthId = uuidOrNull(actorId);
        if (healthId == null) {
            return validation("X-Actor-ID must be the citizen Health ID UUID", requestId, correlationId);
        }

        try {
            JsonNode vitoCard = resolveOrCreateVirtualCard(healthId, publicKey);
            String vitoCardNumber = text(vitoCard, "cardNumber");
            if (vitoCardNumber == null || vitoCardNumber.isBlank()) {
                return upstreamUnavailable("enrollDevice", "VITO card missing cardNumber",
                        requestId, correlationId);
            }

            Map<String, Object> linkBody = new LinkedHashMap<>();
            linkBody.put("vitoCardNumber", vitoCardNumber);
            linkBody.put("publicKey", publicKey);
            JsonNode linked = musheClient.linkVitoCard(musheCardId, linkBody);
            return ok(linked, requestId, correlationId);
        } catch (HttpStatusCodeException ex) {
            Map<String, Object> error = Map.of("code", "UPSTREAM_REJECTED",
                    "message", ex.getStatusText(), "upstreamBody", safeBody(ex));
            log.info("enrollDevice upstream rejected: {} {}", ex.getStatusCode(), safeBody(ex));
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                    "error", error,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", Map.of("code", "ENROLL_CONFLICT", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamUnavailable("enrollDevice",
                    "upstream exception: " + e.getClass().getSimpleName() + ": " + e.getMessage(),
                    requestId, correlationId);
        }
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

    /**
     * Contribute toward a shared bill. Body: {@code {"amount","contributorName","message"}}.
     * Contributor wallet is resolved server-side from the signed-in actor (real debit).
     */
    @PostMapping("/bill-contributions/{shareToken}/contribute")
    public ResponseEntity<Map<String, Object>> contribute(
            @PathVariable String shareToken,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        UUID donorWallet = resolveMyWalletId(actorId);
        if (donorWallet == null) {
            return upstreamUnavailable("contribute", "could not resolve contributor wallet",
                    requestId, correlationId);
        }
        Map<String, Object> upstream = new LinkedHashMap<>(body != null ? body : Map.of());
        upstream.put("contributorWalletId", donorWallet.toString());
        upstream.putIfAbsent("contributorRef", actorId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            upstream.put("idempotencyKey", idempotencyKey);
        }
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

    /**
     * Prefer an existing ACTIVE VITO card (re-provision device key when VIRTUAL); otherwise
     * request + activate a new VIRTUAL credential with the supplied public key.
     */
    private JsonNode resolveOrCreateVirtualCard(UUID healthId, String publicKey) {
        try {
            ResponseEntity<String> active = vitoClient.rawGet("/v1/cards/active/" + healthId);
            JsonNode existing = unwrapVitoData(active.getBody());
            if (existing != null && !existing.isNull()) {
                long id = existing.path("id").asLong(0);
                String form = existing.path("cardForm").asText("PHYSICAL");
                if (id > 0 && "VIRTUAL".equalsIgnoreCase(form)) {
                    ResponseEntity<String> reprovisioned = vitoClient.rawPost(
                            "/v1/cards/" + id + "/provision-device-key",
                            Map.of("publicKey", publicKey));
                    JsonNode updated = unwrapVitoData(reprovisioned.getBody());
                    return updated != null ? updated : existing;
                }
                // Physical active card — link that credential; mushe caches the device key for pay.
                return existing;
            }
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() != 404) {
                throw ex;
            }
            // No active card — create VIRTUAL below.
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("healthId", healthId.toString());
        requestBody.put("publicKey", publicKey);
        requestBody.put("cardForm", "VIRTUAL");
        ResponseEntity<String> createdResp = vitoClient.rawPost("/v1/cards/request", requestBody);
        JsonNode created = unwrapVitoData(createdResp.getBody());
        if (created == null) {
            throw new IllegalStateException("VITO did not return a card on request");
        }
        long id = created.path("id").asLong(0);
        if (id <= 0) {
            throw new IllegalStateException("VITO card missing id");
        }
        ResponseEntity<String> activatedResp = vitoClient.rawPost("/v1/cards/" + id + "/activate", Map.of());
        JsonNode activated = unwrapVitoData(activatedResp.getBody());
        return activated != null ? activated : created;
    }

    private JsonNode unwrapVitoData(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("data") && !root.get("data").isNull()) {
                return root.get("data");
            }
            return root;
        } catch (Exception e) {
            log.debug("Failed to parse VITO response: {}", e.getMessage());
            return null;
        }
    }

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

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText(null);
    }

    private static UUID uuidOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface UpstreamCall {
        JsonNode get();
    }
}
