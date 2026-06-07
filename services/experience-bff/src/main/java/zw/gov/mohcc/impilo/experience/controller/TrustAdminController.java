package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoConsentServiceClient;
import zw.gov.mohcc.impilo.experience.trust.TrustBreakGlassResourceMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trust Administration BFF Controller — bridges the Unified Experience Shell
 * to the TSHEPO Trust Layer cluster (Authz, Consent, Audit).
 *
 * <p>Implements Brief &sect;7 — Trust Layer as Legitimacy Engine. This controller
 * provides a single administrative surface for managing policy rules, reviewing
 * break-glass access requests, governing device trust, querying consent directives,
 * and verifying the immutable audit chain.</p>
 *
 * <p>All endpoints require authenticated access with trust headers forwarded from
 * Envoy/TSHEPO. The controller delegates to the three TSHEPO service clients:</p>
 * <ul>
 *   <li>{@link TshepoAuthzServiceClient} — policy rules, break-glass, device trust</li>
 *   <li>{@link TshepoConsentServiceClient} — consent directives</li>
 *   <li>{@link TshepoAuditServiceClient} — audit events and chain integrity</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/admin/trust")
public class TrustAdminController {

    private static final Logger log = LoggerFactory.getLogger(TrustAdminController.class);

    private final TshepoAuthzServiceClient authzClient;
    private final TshepoConsentServiceClient consentClient;
    private final TshepoAuditServiceClient auditClient;

    public TrustAdminController(TshepoAuthzServiceClient authzClient,
                                TshepoConsentServiceClient consentClient,
                                TshepoAuditServiceClient auditClient) {
        this.authzClient = authzClient;
        this.consentClient = consentClient;
        this.auditClient = auditClient;
    }

    // ── Policy Rules ────────────────────────────────────────────────

    /**
     * GET /internal/v1/admin/trust/policies — list TSHEPO policy rules.
     */
    @GetMapping("/policies")
    public ResponseEntity<Map<String, Object>> listPolicies(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = authzClient.listPolicyRules();
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Trust admin: list policies failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * POST /internal/v1/admin/trust/policies — create a new policy rule.
     */
    @PostMapping("/policies")
    public ResponseEntity<Map<String, Object>> createPolicy(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = authzClient.createPolicyRule(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Trust admin: create policy failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "POLICY_CREATE_FAILED", "message", e.getMessage())));
        }
    }

    // ── Break-Glass Review ──────────────────────────────────────────

    /**
     * GET /internal/v1/admin/trust/break-glass — list pending break-glass reviews.
     */
    @GetMapping("/break-glass")
    public ResponseEntity<Map<String, Object>> listBreakGlassReviews(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = authzClient.getPendingBreakGlassReviews();
            List<Map<String, Object>> resources = TrustBreakGlassResourceMapper.toReviewResources(data);
            return ResponseEntity.ok(Map.of(
                    "data", resources,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Trust admin: list break-glass reviews failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * POST /internal/v1/admin/trust/break-glass/{id}/review — review a break-glass request.
     */
    @PostMapping("/break-glass/{id}/review")
    public ResponseEntity<Map<String, Object>> reviewBreakGlass(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> upstream = new LinkedHashMap<>();
            upstream.put("tenantId", UUID.fromString(tenantId));
            upstream.put("reviewerId", resolveReviewerId(body, actorId));
            upstream.put("approved", resolveApproved(body));
            JsonNode data = authzClient.reviewBreakGlass(id.toString(), upstream);
            return ResponseEntity.ok(Map.of(
                    "data", TrustBreakGlassResourceMapper.toReviewResources(data).stream().findFirst().orElse(Map.of()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Trust admin: break-glass review failed for id={}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "REVIEW_FAILED", "message", e.getMessage())));
        }
    }

    private static String resolveReviewerId(Map<String, Object> body, String actorId) {
        if (actorId != null && !actorId.isBlank()) {
            return actorId;
        }
        Object reviewer = body.get("reviewerId");
        if (reviewer == null) {
            reviewer = body.get("reviewer_id");
        }
        return reviewer != null ? String.valueOf(reviewer) : "system";
    }

    private static boolean resolveApproved(Map<String, Object> body) {
        Object approved = body.get("approved");
        if (approved instanceof Boolean b) {
            return b;
        }
        Object decision = body.get("decision");
        if (decision != null) {
            String normalized = String.valueOf(decision).toUpperCase();
            return "APPROVED".equals(normalized) || "APPROVE".equals(normalized);
        }
        return false;
    }

    // ── Device Trust ────────────────────────────────────────────────

    /**
     * GET /internal/v1/admin/trust/devices/{fingerprint} — get device trust profile.
     */
    @GetMapping("/devices/{fingerprint}")
    public ResponseEntity<Map<String, Object>> getDeviceProfile(
            @PathVariable String fingerprint,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = authzClient.getDeviceProfile(fingerprint);
            if (data == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", Map.of("code", "NOT_FOUND",
                                "message", "Device not found: " + fingerprint)));
            }
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Trust admin: get device profile failed for fingerprint={}: {}", fingerprint, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND",
                            "message", "Device not found: " + fingerprint)));
        }
    }

    /**
     * POST /internal/v1/admin/trust/devices/{fingerprint}/block — block a device.
     */
    @PostMapping("/devices/{fingerprint}/block")
    public ResponseEntity<Map<String, Object>> blockDevice(
            @PathVariable String fingerprint,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = authzClient.blockDevice(fingerprint);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of("fingerprint", fingerprint, "blocked", true),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Trust admin: block device failed for fingerprint={}: {}", fingerprint, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "BLOCK_FAILED", "message", e.getMessage())));
        }
    }

    /**
     * DELETE /internal/v1/admin/trust/devices/{fingerprint}/block — unblock a device.
     */
    @DeleteMapping("/devices/{fingerprint}/block")
    public ResponseEntity<Map<String, Object>> unblockDevice(
            @PathVariable String fingerprint,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            authzClient.unblockDevice(fingerprint);
            return ResponseEntity.ok(Map.of(
                    "data", Map.of("fingerprint", fingerprint, "blocked", false),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Trust admin: unblock device failed for fingerprint={}: {}", fingerprint, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "UNBLOCK_FAILED", "message", e.getMessage())));
        }
    }

    // ── Consent ─────────────────────────────────────────────────────

    /**
     * GET /internal/v1/admin/trust/consents — list consent directives for a subject.
     */
    @GetMapping("/consents")
    public ResponseEntity<Map<String, Object>> listConsents(
            @RequestParam String subjectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = consentClient.listConsents(subjectId, status, page, size);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Trust admin: list consents failed for subject={}: {}", subjectId, e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    // ── Audit ───────────────────────────────────────────────────────

    /**
     * GET /internal/v1/admin/trust/audit — query audit events from TSHEPO Audit.
     */
    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> queryAuditEvents(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String subjectRef,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String fromTime,
            @RequestParam(required = false) String toTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = auditClient.queryEvents(actorId, subjectRef, eventType, fromTime, toTime, page, size);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                            "page", page, "size", size)));
        } catch (Exception e) {
            log.error("Trust admin: audit query failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                            "page", page, "size", size)));
        }
    }

    /**
     * GET /internal/v1/admin/trust/audit/chain/verify — verify audit hash chain integrity.
     */
    @GetMapping("/audit/chain/verify")
    public ResponseEntity<Map<String, Object>> verifyChainIntegrity(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = auditClient.verifyChainIntegrity(Map.of("tenantId", tenantId));
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Trust admin: chain verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", Map.of("code", "CHAIN_VERIFY_FAILED", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
