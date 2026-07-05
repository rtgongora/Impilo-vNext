package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceEmploymentMatchClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider self-service claim / recovery glue (IATG WS-F) — citizen-session-aware.
 *
 * <p>Composition layer only: the person is signed in with their Health ID
 * (X-Actor-ID, per the citizen BFF convention); every downstream call binds to
 * that session identity — the body can never redirect a claim or recovery onto
 * another person. Truth lives in Varapi (provider registry) and, behind a flag,
 * workforce-governance (employment match).</p>
 *
 * <p>Doctrine notes:</p>
 * <ul>
 *   <li><b>Recover-not-reissue</b>: /recover chains Varapi's recovery
 *       initiate + complete and surfaces the SAME providerPublicId (masked) —
 *       a recovery can never mint a new provider identity.</li>
 *   <li><b>Honest pending states</b>: EC-number matching (WS-D lane) is gated
 *       by {@code impilo.features.ec-matching} (default off) and returns 501
 *       FEATURE_PENDING rather than fake success; document upload and org
 *       invitations (Channel C) do the same.</li>
 *   <li><b>Masked at the edge</b>: raw council/EC numbers never travel to the
 *       trust ledger — evidence refs are masked (first two characters) before
 *       leaving the BFF; provider identifiers return masked to the shell.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/provider-claim")
public class ProviderClaimController {

    private static final Logger log = LoggerFactory.getLogger(ProviderClaimController.class);

    static final String EVIDENCE_COUNCIL_NUMBER = "COUNCIL_NUMBER";
    static final String EVIDENCE_EC_NUMBER = "EC_NUMBER";
    static final String EVIDENCE_DOCUMENT = "DOCUMENT";

    private final VarapiServiceClient varapiClient;
    private final WorkforceEmploymentMatchClient employmentMatchClient;
    private final boolean ecMatchingEnabled;

    public ProviderClaimController(
            VarapiServiceClient varapiClient,
            WorkforceEmploymentMatchClient employmentMatchClient,
            @Value("${impilo.features.ec-matching:false}") boolean ecMatchingEnabled) {
        this.varapiClient = varapiClient;
        this.employmentMatchClient = employmentMatchClient;
        this.ecMatchingEnabled = ecMatchingEnabled;
    }

    // ── Eligibility ───────────────────────────────────────────────────────────

    /** Does the session Health ID already have a linked provider profile? */
    @GetMapping("/eligibility")
    public ResponseEntity<Map<String, Object>> eligibility(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        JsonNode linked = varapiClient.getProviderByHealthId(actorId);
        boolean alreadyLinked = linked != null && !linked.isNull();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alreadyLinked", alreadyLinked);
        data.put("eligibleForClaim", !alreadyLinked);
        if (alreadyLinked) {
            data.put("providerPublicId", maskId(text(linked, "providerPublicId")));
        }
        data.put("paths", List.of(
                Map.of("path", "CLAIM_TOKEN", "available", !alreadyLinked),
                Map.of("path", "COUNCIL_NUMBER", "available", !alreadyLinked),
                Map.of("path", "EC_NUMBER", "available", !alreadyLinked && ecMatchingEnabled),
                Map.of("path", "RECOVER", "available", alreadyLinked)));
        return ok(data, requestId, correlationId);
    }

    // ── Claim token: preview then claim ──────────────────────────────────────

    /** Masked preview of what a claim token would claim (confirm-before-commit). */
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body) {

        String claimToken = str(body.get("claimToken"));
        if (claimToken == null || claimToken.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "CLAIM_TOKEN_REQUIRED",
                    "A claim token is required to preview a provider profile.", requestId, correlationId);
        }
        try {
            JsonNode result = varapiClient.claimPreview(Map.of("claimToken", claimToken));
            if (result == null || result.isNull()) {
                return tokenNotRecognised(requestId, correlationId);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("providerPublicId", maskId(text(result, "providerPublicId")));
            data.put("givenNameInitial", initial(text(result, "givenName")));
            data.put("familyName", text(result, "familyName"));
            data.put("profession", text(result, "profession"));
            data.put("lifecycleStatus", text(result, "lifecycleStatus"));
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                return tokenNotRecognised(requestId, correlationId);
            }
            return propagate(e, requestId, correlationId);
        }
    }

    /** Redeem the claim token onto the session Health ID. 409s propagate honestly. */
    @PostMapping("/claim")
    public ResponseEntity<Map<String, Object>> claim(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body) {

        String claimToken = str(body.get("claimToken"));
        if (claimToken == null || claimToken.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "CLAIM_TOKEN_REQUIRED",
                    "A claim token is required to claim a provider profile.", requestId, correlationId);
        }
        if (!Boolean.TRUE.equals(body.get("consent"))) {
            return error(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED",
                    "Explicit consent is required before linking a provider profile to your Health ID.",
                    requestId, correlationId);
        }
        try {
            // The claimant is ALWAYS the session Health ID — never taken from the body.
            JsonNode result = varapiClient.claim(Map.of(
                    "claimToken", claimToken,
                    "claimantHealthId", actorId));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("linked", true);
            data.put("providerPublicId", maskId(text(result, "providerPublicId")));
            data.put("lifecycleStatus", text(result, "lifecycleStatus"));
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            // 409 (token burned / profile not claimable / one-person-one-profile)
            // and every other downstream verdict propagate honestly.
            return propagate(e, requestId, correlationId);
        }
    }

    // ── Evidence submission ───────────────────────────────────────────────────

    /**
     * Submit identity evidence: council number (recorded on the trust ledger,
     * masked), EC number (WS-D employment match, flag-gated), or document
     * (honest 501 until the document lane lands).
     */
    @PostMapping("/evidence")
    public ResponseEntity<Map<String, Object>> evidence(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body) {

        String type = str(body.get("type"));
        String value = str(body.get("value"));
        if (type == null || value == null || value.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "EVIDENCE_INCOMPLETE",
                    "Evidence type and value are both required.", requestId, correlationId);
        }

        switch (type) {
            case EVIDENCE_COUNCIL_NUMBER:
                return councilNumberEvidence(value, str(body.get("councilCode")), requestId, correlationId);
            case EVIDENCE_EC_NUMBER:
                return ecNumberEvidence(value, actorId, requestId, correlationId);
            case EVIDENCE_DOCUMENT:
                return error(HttpStatus.NOT_IMPLEMENTED, "FEATURE_PENDING",
                        "Document-based verification is not available yet. Your council number, a claim "
                                + "token from your organisation, or a registry desk remain the supported routes.",
                        requestId, correlationId);
            default:
                return error(HttpStatus.BAD_REQUEST, "EVIDENCE_TYPE_UNSUPPORTED",
                        "Evidence type must be COUNCIL_NUMBER, EC_NUMBER, or DOCUMENT.",
                        requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> councilNumberEvidence(
            String councilNumber, String councilCode, String requestId, String correlationId) {
        try {
            JsonNode resolution = varapiClient.resolveCouncilNumber(councilNumber, councilCode);
            boolean resolved = resolution != null && resolution.path("resolved").asBoolean(false);
            if (!resolved) {
                return error(HttpStatus.NOT_FOUND, "COUNCIL_NUMBER_UNRESOLVED",
                        "That council registration could not be matched to a provider profile. "
                                + "Check the number and council, or contact your council registry.",
                        requestId, correlationId);
            }
            String providerPublicId = text(resolution.path("providerRef"), "providerPublicId");
            // WS-C trust API contract; the ref is masked BEFORE leaving the BFF.
            varapiClient.recordVerificationAttempt(providerPublicId, Map.of(
                    "type", EVIDENCE_COUNCIL_NUMBER,
                    "source", "COUNCIL_RECORD",
                    "ref", maskRef(councilNumber),
                    "outcome", "PENDING",
                    "confidence", 0.5));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "RECORDED");
            data.put("evidenceRef", maskRef(councilNumber));
            data.put("providerPublicId", maskId(providerPublicId));
            data.put("nextStep", "Verification is pending council-record review. You will be notified; "
                    + "no provider role is activated until verification completes.");
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(envelope(data, requestId, correlationId));
        } catch (HttpStatusCodeException e) {
            log.warn("council-number evidence path unavailable: {}", e.getStatusCode());
            return error(HttpStatus.BAD_GATEWAY, "VERIFICATION_CHANNEL_UNAVAILABLE",
                    "The council verification channel is temporarily unavailable. Nothing was recorded — "
                            + "please try again later.", requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> ecNumberEvidence(
            String ecNumber, String actorId, String requestId, String correlationId) {
        if (!ecMatchingEnabled) {
            // WS-D lane not enabled — honest pending state, never fake success.
            return error(HttpStatus.NOT_IMPLEMENTED, "FEATURE_PENDING",
                    "EC-number employment matching is not enabled yet. Once the workforce employment "
                            + "registry lane is live this route will activate; until then use your council "
                            + "number or a claim token from your organisation.",
                    requestId, correlationId);
        }
        try {
            JsonNode match = employmentMatchClient.matchEmployment(Map.of(
                    "ecNumber", ecNumber,
                    "healthId", actorId));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "MATCH_ATTEMPTED");
            data.put("evidenceRef", maskRef(ecNumber));
            data.put("matchStatus", match != null ? text(match, "matchStatus") : null);
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    // ── Recovery (recover-not-reissue) ────────────────────────────────────────

    /**
     * Recover an existing provider profile for the session Health ID. Chains
     * Varapi recovery initiate → complete; the response carries the SAME
     * providerPublicId (masked) the profile always had — never a new one.
     */
    @PostMapping("/recover")
    public ResponseEntity<Map<String, Object>> recover(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body) {

        Object rawEvidence = body.get("evidence");
        if (!(rawEvidence instanceof Map<?, ?> evidenceMap)
                || str(evidenceMap.get("type")) == null) {
            return error(HttpStatus.BAD_REQUEST, "EVIDENCE_INCOMPLETE",
                    "Recovery requires evidence with at least a type.", requestId, correlationId);
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("type", str(evidenceMap.get("type")));
        if (str(evidenceMap.get("source")) != null) {
            evidence.put("source", str(evidenceMap.get("source")));
        }
        String ref = str(evidenceMap.get("ref")) != null
                ? str(evidenceMap.get("ref")) : str(evidenceMap.get("value"));
        if (ref != null && !ref.isBlank()) {
            // Raw evidence values never travel downstream — masked at the edge.
            evidence.put("ref", maskRef(ref));
        }
        if (evidenceMap.get("confidence") instanceof Number n) {
            evidence.put("confidence", n.doubleValue());
        }

        try {
            JsonNode initiated = varapiClient.recoveryInitiate(Map.of(
                    "healthId", actorId,
                    "evidence", evidence));
            String issuedFor = text(initiated, "providerPublicId");
            String recoveryToken = text(initiated, "recoveryToken");

            JsonNode completed = varapiClient.recoveryComplete(Map.of("token", recoveryToken));
            String recoveredId = text(completed, "providerPublicId");

            if (recoveredId == null || !recoveredId.equals(issuedFor)) {
                // Recover-not-reissue integrity: the identifier must be unchanged.
                log.error("recovery integrity violation: initiated={} completed={}",
                        maskId(issuedFor), maskId(recoveredId));
                return error(HttpStatus.BAD_GATEWAY, "RECOVERY_INTEGRITY",
                        "Recovery could not be completed safely and was not applied. No new provider "
                                + "identity was created. Please contact the registry.", requestId, correlationId);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("recovered", true);
            data.put("providerPublicId", maskId(recoveredId));
            data.put("lifecycleStatus", text(completed, "lifecycleStatus"));
            data.put("note", "Your existing provider profile was re-linked to this login. "
                    + "The Provider ID is unchanged — recovery never issues a new one.");
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                return error(HttpStatus.NOT_FOUND, "NO_LINKED_PROFILE",
                        "No provider profile is linked to this Health ID, so there is nothing to recover. "
                                + "If you were preloaded by your organisation, use a claim token instead.",
                        requestId, correlationId);
            }
            return propagate(e, requestId, correlationId);
        }
    }

    // ── Channel C placeholder ─────────────────────────────────────────────────

    /** Organisation-invitation onboarding — arrives with org-registry adoption (Channel C). */
    @PostMapping("/org-invitation")
    public ResponseEntity<Map<String, Object>> orgInvitation(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {
        return error(HttpStatus.NOT_IMPLEMENTED, "FEATURE_PENDING",
                "Organisation invitations arrive with the org-registry adoption wave (Channel C). "
                        + "Ask your organisation for a claim token, or use your council number.",
                requestId, correlationId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> tokenNotRecognised(String requestId, String correlationId) {
        return error(HttpStatus.NOT_FOUND, "TOKEN_NOT_RECOGNISED",
                "That claim token is not recognised. It may have expired, already been used, or been "
                        + "revoked — Varapi does not reveal which. Contact the issuer for a new token.",
                requestId, correlationId);
    }

    private ResponseEntity<Map<String, Object>> ok(
            Map<String, Object> data, String requestId, String correlationId) {
        return ResponseEntity.ok(envelope(data, requestId, correlationId));
    }

    private Map<String, Object> envelope(Map<String, Object> data, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return response;
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    /** Propagate a downstream verdict (status + message) honestly — never remap 409s. */
    private ResponseEntity<Map<String, Object>> propagate(
            HttpStatusCodeException e, String requestId, String correlationId) {
        String message = e.getResponseBodyAsString();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                "error", Map.of(
                        "code", "UPSTREAM_" + e.getStatusCode().value(),
                        "message", message != null && !message.isBlank()
                                ? message : e.getStatusText()),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    /** Mask a provider public identifier: first 4 + *** + last 2. */
    static String maskId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String v = id.trim();
        if (v.length() <= 6) {
            return v.charAt(0) + "***";
        }
        return v.substring(0, 4) + "***" + v.substring(v.length() - 2);
    }

    /** Mask an evidence reference: first 2 + ***. */
    static String maskRef(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        return (v.length() <= 2 ? v : v.substring(0, 2)) + "***";
    }

    private static String initial(String name) {
        return name == null || name.isBlank() ? null : name.trim().charAt(0) + ".";
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String str(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}
