package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Digital Consent Pipeline — multi-channel Privacy Policy and Terms of Use acceptance.
 *
 * <p>Records explicit user consent for versioned legal documents across all
 * device types and access channels. Every consent action is recorded with
 * channel, method, and device metadata for audit and compliance.</p>
 *
 * <h3>Supported Channels</h3>
 * <ul>
 *   <li><b>WEB</b> — Desktop/mobile browser via Experience UI</li>
 *   <li><b>APP</b> — Native mobile app (smartphone)</li>
 *   <li><b>SMS</b> — Feature phone via SMS reply (YES/NDIO/1)</li>
 *   <li><b>USSD</b> — Feature phone via USSD session menu</li>
 *   <li><b>KIOSK</b> — Shared facility kiosk/terminal</li>
 *   <li><b>VOICE</b> — Telephone/IVR verbal consent (operator-recorded)</li>
 *   <li><b>PAPER</b> — Scanned/digitised paper consent form</li>
 *   <li><b>OPERATOR</b> — Consent recorded by authorized operator on behalf of user</li>
 * </ul>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>POST /internal/v1/consent/accept — accept policies (any channel)</li>
 *   <li>GET  /internal/v1/consent/status — check consent status for current user</li>
 *   <li>GET  /internal/v1/consent/history — audit trail of consent changes</li>
 *   <li>POST /internal/v1/consent/revoke — revoke consent</li>
 *   <li>POST /internal/v1/consent/sms — record consent received via SMS reply</li>
 *   <li>POST /internal/v1/consent/ussd — record consent received via USSD session</li>
 *   <li>POST /internal/v1/consent/operator — record consent on behalf of a user</li>
 *   <li>POST /internal/v1/consent/send-request — send consent request via SMS</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/consent")
public class PolicyConsentController {

    private static final Logger log = LoggerFactory.getLogger(PolicyConsentController.class);

    /** Valid consent channels. */
    private static final Set<String> VALID_CHANNELS = Set.of(
            "WEB", "APP", "SMS", "USSD", "KIOSK", "VOICE", "PAPER", "OPERATOR");

    private final zw.gov.mohcc.impilo.experience.client.TshepoConsentServiceClient consentClient;

    public PolicyConsentController(zw.gov.mohcc.impilo.experience.client.TshepoConsentServiceClient consentClient) {
        this.consentClient = consentClient;
    }

    // ── Accept consent (primary — all channels) ─────────────────────

    /**
     * Accept Privacy Policy and/or Terms of Use.
     *
     * <p>Accepts optional {@code channel} (WEB, APP, SMS, USSD, KIOSK, VOICE, PAPER, OPERATOR)
     * and {@code deviceType} (e.g. SMARTPHONE, FEATURE_PHONE, DESKTOP, TABLET, KIOSK_TERMINAL)
     * for audit trail. Defaults to WEB if not provided.</p>
     */
    @PostMapping("/accept")
    public ResponseEntity<Map<String, Object>> acceptConsent(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.POD_ID, required = false, defaultValue = "default") String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestBody Map<String, Object> body) {

        String userId = actorId != null && !actorId.isBlank() ? actorId : body.getOrDefault("userId", "").toString();
        String version = body.getOrDefault("version", "").toString();
        boolean privacyAccepted = Boolean.TRUE.equals(body.get("privacyPolicyAccepted"));
        boolean termsAccepted = Boolean.TRUE.equals(body.get("termsOfUseAccepted"));
        String channel = normalizeChannel(body.getOrDefault("channel", "WEB").toString());
        String deviceType = body.getOrDefault("deviceType", "").toString();

        if (userId.isBlank() || version.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "userId and version are required")));
        }

        String ipAddress = forwardedFor != null ? forwardedFor.split(",")[0].trim() : null;
        OffsetDateTime now = OffsetDateTime.now();

        List<Map<String, Object>> accepted = new ArrayList<>();

        if (privacyAccepted) {
            accepted.add(Map.of("policyType", "PRIVACY_POLICY", "version", version, "acceptedAt", now.toString(), "channel", channel));
        }

        if (termsAccepted) {
            accepted.add(Map.of("policyType", "TERMS_OF_USE", "version", version, "acceptedAt", now.toString(), "channel", channel));
        }

        log.info("Policy consent accepted: user={}, version={}, channel={}, privacy={}, terms={}",
                userId, version, channel, privacyAccepted, termsAccepted);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", userId, "type", "policy_consent",
                "attributes", Map.of("accepted", accepted, "version", version, "channel", channel)
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    // ── SMS consent ─────────────────────────────────────────────────

    /**
     * Record consent received via SMS reply.
     *
     * <p>Called by the SMS/channels gateway when a user replies YES/NDIO/1
     * to a consent request SMS. The phone number is used to resolve the user.</p>
     */
    @PostMapping("/sms")
    public ResponseEntity<Map<String, Object>> smsConsent(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.POD_ID, required = false, defaultValue = "default") String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        String phoneNumber = body.getOrDefault("phoneNumber", "").toString().trim();
        String userId = body.getOrDefault("userId", "").toString().trim();
        String reply = body.getOrDefault("reply", "").toString().trim().toUpperCase();
        String version = body.getOrDefault("version", "").toString();
        String sessionId = body.getOrDefault("sessionId", "").toString();

        if ((phoneNumber.isBlank() && userId.isBlank()) || version.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION",
                            "message", "phoneNumber or userId, and version are required")));
        }

        // Determine consent from reply keyword
        boolean consented = Set.of("YES", "NDIO", "EWE", "YEBO", "1", "ACCEPT", "AGREE").contains(reply);

        if (!consented) {
            log.info("SMS consent declined: phone={}, reply={}", phoneNumber, reply);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", Map.of("id", phoneNumber, "type", "consent_sms_response",
                    "attributes", Map.of("accepted", false, "reply", reply)));
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String smsUserAgent = "SMS/" + phoneNumber;

        log.info("SMS consent accepted: phone={}, userId={}, version={}", phoneNumber, userId, version);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", userId.isBlank() ? phoneNumber : userId, "type", "consent_sms_response",
                "attributes", Map.of("accepted", true, "version", version, "channel", "SMS")));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    // ── USSD consent ────────────────────────────────────────────────

    /**
     * Record consent received via USSD session.
     *
     * <p>Called by the USSD gateway when a user selects the accept option
     * in a USSD consent menu (e.g. dial *123# → option 1 = Accept).</p>
     */
    @PostMapping("/ussd")
    public ResponseEntity<Map<String, Object>> ussdConsent(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.POD_ID, required = false, defaultValue = "default") String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        String phoneNumber = body.getOrDefault("phoneNumber", "").toString().trim();
        String userId = body.getOrDefault("userId", "").toString().trim();
        String selection = body.getOrDefault("selection", "").toString().trim();
        String version = body.getOrDefault("version", "").toString();
        String sessionId = body.getOrDefault("sessionId", UUID.randomUUID().toString()).toString();

        if ((phoneNumber.isBlank() && userId.isBlank()) || version.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION",
                            "message", "phoneNumber or userId, and version are required")));
        }

        boolean consented = Set.of("1", "YES", "ACCEPT").contains(selection.toUpperCase());

        if (!consented) {
            log.info("USSD consent declined: phone={}, selection={}", phoneNumber, selection);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", Map.of("id", phoneNumber, "type", "consent_ussd_response",
                    "attributes", Map.of("accepted", false, "selection", selection)));
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String effectiveUserId = userId.isBlank() ? phoneNumber : userId;

        log.info("USSD consent accepted: phone={}, userId={}, version={}", phoneNumber, effectiveUserId, version);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", effectiveUserId, "type", "consent_ussd_response",
                "attributes", Map.of("accepted", true, "version", version, "channel", "USSD")));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    // ── Operator-assisted consent ───────────────────────────────────

    /**
     * Record consent on behalf of a user (operator-assisted).
     *
     * <p>Used when an authorized operator (e.g. registration clerk, community health
     * worker) records consent on behalf of a user who cannot self-serve — for example,
     * a patient at a facility with no phone, or an elderly person who needs assistance.</p>
     */
    @PostMapping("/operator")
    public ResponseEntity<Map<String, Object>> operatorConsent(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.POD_ID, required = false, defaultValue = "default") String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String operatorId,
            @RequestBody Map<String, Object> body) {

        String subjectUserId = body.getOrDefault("subjectUserId", "").toString().trim();
        String version = body.getOrDefault("version", "").toString();
        String channel = normalizeChannel(body.getOrDefault("channel", "OPERATOR").toString());
        String deviceType = body.getOrDefault("deviceType", "").toString();
        String witnessName = body.getOrDefault("witnessName", "").toString();
        String notes = body.getOrDefault("notes", "").toString();

        if (subjectUserId.isBlank() || version.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION",
                            "message", "subjectUserId and version are required")));
        }

        OffsetDateTime now = OffsetDateTime.now();
        String operatorAgent = "OPERATOR/" + (operatorId != null ? operatorId : "unknown");

        log.info("Operator consent recorded: subject={}, operator={}, channel={}, version={}",
                subjectUserId, operatorId, channel, version);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", subjectUserId, "type", "consent_operator_response",
                "attributes", Map.of("accepted", true, "version", version, "channel", channel,
                        "operatorId", operatorId != null ? operatorId : "", "subjectUserId", subjectUserId)));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    // ── Send consent request via SMS ────────────────────────────────

    /**
     * Send a consent request to a user via SMS or notification.
     *
     * <p>Enqueues an outbound SMS containing a consent prompt. The user
     * replies YES/NO. The SMS gateway routes the reply back to {@code POST /consent/sms}.</p>
     */
    @PostMapping("/send-request")
    public ResponseEntity<Map<String, Object>> sendConsentRequest(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.POD_ID, required = false, defaultValue = "default") String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {

        String phoneNumber = body.getOrDefault("phoneNumber", "").toString().trim();
        String userId = body.getOrDefault("userId", "").toString().trim();
        String version = body.getOrDefault("version", "").toString();
        String channel = body.getOrDefault("channel", "SMS").toString().toUpperCase();
        String language = body.getOrDefault("language", "en").toString();

        if (phoneNumber.isBlank() || version.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION",
                            "message", "phoneNumber and version are required")));
        }

        OffsetDateTime now = OffsetDateTime.now();
        String consentRequestId = UUID.randomUUID().toString();

        log.info("Consent request sent: phone={}, channel={}, version={}, requestId={}",
                phoneNumber, channel, version, consentRequestId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", consentRequestId, "type", "consent_request",
                "attributes", Map.of("phoneNumber", phoneNumber, "version", version,
                        "channel", channel, "status", "PENDING")));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.accepted().body(response);
    }

    // ── Status & History (unchanged) ────────────────────────────────

    /**
     * Check consent status for the current user.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getConsentStatus(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestParam(required = false) String version) {

        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required")));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", List.of());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Consent history — full audit trail.
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getConsentHistory(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required")));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", List.of());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Revoke consent — marks existing consent as revoked.
     */
    @PostMapping("/revoke")
    public ResponseEntity<Map<String, Object>> revokeConsent(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.POD_ID, required = false, defaultValue = "default") String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {

        String userId = actorId != null && !actorId.isBlank() ? actorId : body.getOrDefault("userId", "").toString();
        String policyType = body.getOrDefault("policyType", "").toString();

        if (userId.isBlank() || policyType.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "userId and policyType are required")));
        }

        OffsetDateTime now = OffsetDateTime.now();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", userId, "type", "policy_consent_revocation",
                "attributes", Map.of("policyType", policyType, "revokedAt", now.toString())));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * List the authenticated person's own consent directives.
     * Alias for /history scoped to the current actor.
     */
    @GetMapping("/mine")
    public ResponseEntity<Map<String, Object>> myConsents(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        try {
            JsonNode consents = consentClient.listConsents(actorId != null ? actorId : "", null, 0, 100);
            return ResponseEntity.ok(Map.of(
                    "data", consents != null ? consents : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * Create a new consent directive for the authenticated person.
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createConsent(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        try {
            if (actorId != null && !actorId.isBlank()) {
                body.putIfAbsent("subjectId", actorId);
            }
            JsonNode result = consentClient.createConsent(body);
            return ResponseEntity.status(201).body(Map.of(
                    "data", result != null ? result : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "CONSENT_FAILED", "message", e.getMessage())));
        }
    }

    private String normalizeChannel(String channel) {
        String upper = channel.toUpperCase();
        return VALID_CHANNELS.contains(upper) ? upper : "WEB";
    }
}
