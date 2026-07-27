package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The person-assurance gate on the regulator bootstrap rail (RB-3b).
 *
 * <p>workforce-governance owns the founding-administrator request, its four-eyes approval and the
 * activation event. It deliberately does <b>not</b> gate on person assurance: a sovereign service
 * cannot trust a body-supplied assurance claim, and the identity-assurance service is reachable
 * here, where the facility- and provider-claim gates already sit. So the gate lives at the BFF, and
 * this controller is it.</p>
 *
 * <p><b>Why LOA3, not LOA2.</b> A facility claim requires LOA2. Founding a regulator's administration
 * is a stronger act — it seeds the trust root of a statutory body — and {@code AssurancePolicy}
 * already places {@code ADMIN_OPERATIONS} and {@code PROVIDER_ACTIVATION} at LOA4. LOA3 (an
 * in-person / authoritatively-verified identity) is the floor for both submitting a bootstrap
 * request and approving one: each of the two approvers must themselves be proven, or four-eyes is
 * four-eyes over unproven people.</p>
 *
 * <p>The gate is <b>fail-closed</b>: if the assurance service is unreachable the claimant is treated
 * as LOA1 and refused, exactly as the facility- and provider-claim gates do — an outage must never
 * become an open door onto the trust root.</p>
 */
@RestController
@RequestMapping("/api/v1/regulator-bootstrap")
public class RegulatorBootstrapController {

    private static final Logger log = LoggerFactory.getLogger(RegulatorBootstrapController.class);

    /** Founding a regulator's administration demands an authoritatively-verified identity. */
    private static final int MIN_BOOTSTRAP_ASSURANCE_RANK = 3; // LOA3

    private final WorkforceGovernanceClient governanceClient;
    private final IdentityAssuranceServiceClient assuranceClient;

    public RegulatorBootstrapController(WorkforceGovernanceClient governanceClient,
                                        IdentityAssuranceServiceClient assuranceClient) {
        this.governanceClient = governanceClient;
        this.assuranceClient = assuranceClient;
    }

    /**
     * Submit a founding-administrator bootstrap request. The claimant is the authenticated person
     * ({@code X-Actor-ID}), never a body field — nobody self-claims on someone else's behalf.
     */
    @PostMapping("/requests")
    public ResponseEntity<Map<String, Object>> submit(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestBody Map<String, Object> body) {

        ResponseEntity<Map<String, Object>> denied = assuranceGate(requestId, correlationId,
                "Confirm your personal identity to an authoritative level before requesting to found "
                        + "a regulator's administration. Complete identity verification, then try again.");
        if (denied != null) {
            return denied;
        }

        Map<String, Object> proxied = new LinkedHashMap<>(body);
        // The claimant is the signed-in person, taken from the trusted header — never a body value.
        proxied.put("claimantHealthId", actorId);
        JsonNode result = governanceClient.submitBootstrapRequest(proxied);
        return relay(result, HttpStatus.CREATED, requestId, correlationId,
                "The bootstrap request could not be submitted.");
    }

    /**
     * Record one approver's decision. Each approver must themselves be proven — four-eyes over an
     * unproven approver is not four-eyes.
     */
    @PostMapping("/actions/{accessRequestId}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @PathVariable String accessRequestId,
            @RequestBody(required = false) Map<String, Object> body) {

        ResponseEntity<Map<String, Object>> denied = assuranceGate(requestId, correlationId,
                "Confirm your personal identity to an authoritative level before approving a "
                        + "founding-administrator appointment.");
        if (denied != null) {
            return denied;
        }

        Map<String, Object> proxied = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        proxied.put("approverUserId", actorId);
        JsonNode result = governanceClient.approveBootstrap(accessRequestId, proxied);
        return relay(result, HttpStatus.OK, requestId, correlationId,
                "The approval could not be recorded.");
    }

    /** List bootstrap requests for the console (read; no assurance gate on a read). */
    @GetMapping("/requests")
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String organisationId,
            @RequestParam(required = false) String status) {
        JsonNode result = governanceClient.listBootstrapRequests(organisationId, status);
        return relay(result, HttpStatus.OK, requestId, correlationId,
                "The bootstrap requests could not be listed.");
    }

    // ── the gate ──────────────────────────────────────────────────────────────

    /** Returns a 403 response when the claimant is below LOA3, or null when the gate passes. */
    private ResponseEntity<Map<String, Object>> assuranceGate(String requestId, String correlationId,
                                                              String message) {
        if (claimantAssuranceRank() < MIN_BOOTSTRAP_ASSURANCE_RANK) {
            return error(HttpStatus.FORBIDDEN, "IDENTITY_PROOFING_REQUIRED", message,
                    requestId, correlationId);
        }
        return null;
    }

    /**
     * Fail-closed assurance rank. An identity-assurance outage is treated as LOA1 and refused — an
     * outage must never open the door onto a regulator's trust root.
     */
    private int claimantAssuranceRank() {
        try {
            JsonNode status = assuranceClient.getAssuranceStatus();
            String level = status == null ? null : status.path("currentLevel").asText(null);
            return switch (level == null ? "LOA1" : level.trim().toUpperCase(Locale.ROOT)) {
                case "LOA4" -> 4;
                case "LOA3" -> 3;
                case "LOA2" -> 2;
                default -> 1;
            };
        } catch (Exception e) {
            log.warn("assurance status unavailable — treating as LOA1 (fail-closed): {}", e.getMessage());
            return 1;
        }
    }

    private ResponseEntity<Map<String, Object>> relay(JsonNode data, HttpStatus okStatus,
                                                      String requestId, String correlationId,
                                                      String failMessage) {
        if (data == null || data.isMissingNode() || data.isNull()) {
            return error(HttpStatus.BAD_GATEWAY, "GOVERNANCE_UNAVAILABLE", failMessage,
                    requestId, correlationId);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(okStatus).body(response);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus statusCode, String code,
                                                      String message, String requestId, String correlationId) {
        return ResponseEntity.status(statusCode).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
