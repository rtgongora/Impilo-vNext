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
 * Identity Assurance BFF Controller — surfaces the user's current identity assurance
 * state and provides pathways to upgrade assurance level.
 *
 * <p>Implements the Health OS doctrine's assurance level dimension (one of the 10
 * access control dimensions). The assurance level (LOA1 through LOA4) determines
 * what operations a person can perform. Higher-friction operations such as
 * prescribing and claims submission require LOA3 or LOA4, while wellness tracking
 * and search operate at LOA1.</p>
 *
 * <p>Assurance levels:</p>
 * <ul>
 *   <li><strong>LOA1</strong> — Self-asserted identity (email/phone verification)</li>
 *   <li><strong>LOA2</strong> — Remote identity proofing (document upload + liveness check)</li>
 *   <li><strong>LOA3</strong> — In-person identity verification or supervised remote proofing</li>
 *   <li><strong>LOA4</strong> — Physical token or biometric binding with in-person verification</li>
 * </ul>
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>GET  /internal/v1/identity/assurance/status — current assurance state and permissions</li>
 *   <li>POST /internal/v1/identity/assurance/upgrade/request — initiate assurance upgrade</li>
 * </ul>
 *
 * @see <a href="docs/doctrine/health-os-doctrine.md">Health OS Doctrine — Access Control (10 Dimensions)</a>
 */
@RestController
@RequestMapping("/internal/v1/identity/assurance")
public class IdentityAssuranceController {

    private static final Logger log = LoggerFactory.getLogger(IdentityAssuranceController.class);

    /**
     * Returns the current user's identity assurance state.
     *
     * <p>Provides the current assurance level, completed verification steps,
     * available upgrade pathways, and the permissions/restrictions that apply
     * at the current level. This powers the experience shell's security
     * dashboard and contextual upgrade prompts.</p>
     *
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @param actorId       the Health ID of the person (X-Actor-ID), optional for context
     * @return assurance status with current level, permissions, and upgrade pathways
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAssuranceStatus(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        log.info("Getting assurance status: tenant={}, actorId={}, requestId={}",
                tenantId, actorId, requestId);

        // Placeholder: realistic assurance state
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("currentLevel", "LOA2");
        status.put("actorId", actorId != null ? actorId : "unknown");
        status.put("assessedAt", OffsetDateTime.now().minusHours(2).toString());

        // Completed verification steps
        status.put("completedSteps", List.of(
                Map.of("step", "EMAIL_VERIFICATION", "completedAt",
                        OffsetDateTime.now().minusDays(90).toString(), "level", "LOA1"),
                Map.of("step", "PHONE_VERIFICATION", "completedAt",
                        OffsetDateTime.now().minusDays(89).toString(), "level", "LOA1"),
                Map.of("step", "DOCUMENT_UPLOAD", "completedAt",
                        OffsetDateTime.now().minusDays(60).toString(), "level", "LOA2"),
                Map.of("step", "LIVENESS_CHECK", "completedAt",
                        OffsetDateTime.now().minusDays(60).toString(), "level", "LOA2")
        ));

        // Permissions at current level
        status.put("permissions", Map.of(
                "granted", List.of(
                        "VIEW_OWN_RECORDS",
                        "WELLNESS_TRACKING",
                        "APPOINTMENT_BOOKING",
                        "MESSAGING",
                        "SEARCH",
                        "MARKETPLACE_BROWSE",
                        "EDUCATION_ACCESS",
                        "COMMUNITY_PARTICIPATION"
                ),
                "restricted", List.of(
                        "PRESCRIBING",
                        "CLAIMS_SUBMISSION",
                        "IDENTITY_VERIFICATION_OTHERS",
                        "ADMIN_OPERATIONS",
                        "PROVIDER_ACTIVATION"
                )
        ));

        // Available upgrade pathways
        status.put("upgradePathways", List.of(
                Map.of(
                        "targetLevel", "LOA3",
                        "method", "IN_PERSON_VERIFICATION",
                        "description", "Visit a registered facility for in-person identity verification",
                        "requiredDocuments", List.of("National ID or Passport", "Proof of address"),
                        "estimatedDuration", "15 minutes",
                        "availableFacilities", 42
                ),
                Map.of(
                        "targetLevel", "LOA3",
                        "method", "SUPERVISED_REMOTE_PROOFING",
                        "description", "Video call with an identity verification officer",
                        "requiredDocuments", List.of("National ID or Passport"),
                        "estimatedDuration", "20 minutes",
                        "availableSlots", 8
                ),
                Map.of(
                        "targetLevel", "LOA4",
                        "method", "BIOMETRIC_BINDING",
                        "description", "In-person biometric enrollment with physical token issuance",
                        "requiredDocuments", List.of("National ID", "Proof of address", "Passport photo"),
                        "estimatedDuration", "30 minutes",
                        "prerequisite", "LOA3"
                )
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", status);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Initiate an assurance level upgrade request.
     *
     * <p>Creates an upgrade request for the specified target level and method.
     * The request is placed in a processing queue and the user is notified of
     * next steps (e.g., facility visit, document upload, video call scheduling).</p>
     *
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @param body          upgrade request with targetLevel and method
     * @return upgrade request confirmation with tracking ID and next steps
     */
    @PostMapping("/upgrade/request")
    public ResponseEntity<Map<String, Object>> requestUpgrade(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        String targetLevel = body.getOrDefault("targetLevel", "LOA3").toString();
        String method = body.getOrDefault("method", "IN_PERSON_VERIFICATION").toString();

        log.info("Assurance upgrade requested: tenant={}, targetLevel={}, method={}, requestId={}",
                tenantId, targetLevel, method, requestId);

        // Placeholder: upgrade request response
        String upgradeRequestId = "UPG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Map<String, Object> upgrade = new LinkedHashMap<>();
        upgrade.put("upgradeRequestId", upgradeRequestId);
        upgrade.put("currentLevel", "LOA2");
        upgrade.put("targetLevel", targetLevel);
        upgrade.put("method", method);
        upgrade.put("status", "PENDING");
        upgrade.put("createdAt", OffsetDateTime.now().toString());
        upgrade.put("expiresAt", OffsetDateTime.now().plusDays(30).toString());

        // Next steps based on method
        List<Map<String, Object>> nextSteps = new ArrayList<>();
        if ("IN_PERSON_VERIFICATION".equals(method)) {
            nextSteps.add(Map.of("step", 1, "action", "SELECT_FACILITY",
                    "description", "Choose a verification facility near you"));
            nextSteps.add(Map.of("step", 2, "action", "BOOK_APPOINTMENT",
                    "description", "Schedule your verification appointment"));
            nextSteps.add(Map.of("step", 3, "action", "BRING_DOCUMENTS",
                    "description", "Bring your National ID and proof of address to the appointment"));
        } else if ("SUPERVISED_REMOTE_PROOFING".equals(method)) {
            nextSteps.add(Map.of("step", 1, "action", "UPLOAD_DOCUMENTS",
                    "description", "Upload clear photos of your National ID (front and back)"));
            nextSteps.add(Map.of("step", 2, "action", "SCHEDULE_VIDEO_CALL",
                    "description", "Select an available time slot for your verification call"));
            nextSteps.add(Map.of("step", 3, "action", "ATTEND_CALL",
                    "description", "Join the video call at your scheduled time with your ID ready"));
        }
        upgrade.put("nextSteps", nextSteps);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", upgrade);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
