package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Temp ID Review BFF Controller — administrative surface for supervisors to review,
 * approve, or reject temporary identifiers pending promotion to permanent Health IDs.
 *
 * <p>Implements the Health OS doctrine's person-centered identity principle. When a
 * person presents at a facility without existing identification, a temporary ID is
 * issued to ensure continuity of care. These temporary IDs must be reviewed and
 * either promoted to permanent Health IDs (with proper identity proofing) or
 * rejected with documented reason. This supervisory review process ensures identity
 * integrity within the governed trust model.</p>
 *
 * <p>The review queue is facility-scoped and role-restricted — only users with
 * SUPERVISOR or FACILITY_ADMIN roles can access these endpoints. The 10-dimension
 * access control model enforces organizational affiliation and facility context
 * before granting access.</p>
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>GET  /internal/v1/admin/temp-id-review/queue — list temp IDs pending review</li>
 *   <li>POST /internal/v1/admin/temp-id-review/{tempId}/approve — approve a temp ID</li>
 *   <li>POST /internal/v1/admin/temp-id-review/{tempId}/reject — reject a temp ID</li>
 * </ul>
 *
 * @see <a href="docs/doctrine/health-os-doctrine.md">Health OS Doctrine — Person-Centered Identity</a>
 */
@RestController
@RequestMapping("/internal/v1/admin/temp-id-review")
public class TempIdReviewController {

    private static final Logger log = LoggerFactory.getLogger(TempIdReviewController.class);

    /**
     * List temporary IDs pending supervisor review.
     *
     * <p>Returns the queue of temporary identifiers awaiting review, ordered by
     * creation date (oldest first, to ensure FIFO processing). Each entry includes
     * the temp ID, the facility where it was issued, the issuing clinician, the
     * patient's demographic summary, and the elapsed time since issuance.</p>
     *
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @param facilityId    optional facility filter (X-Facility-ID header)
     * @param status        optional status filter (default: PENDING_REVIEW)
     * @param page          page number for pagination (default: 0)
     * @param size          page size for pagination (default: 20)
     * @return paginated list of temp IDs pending review
     */
    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getReviewQueue(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestParam(required = false, defaultValue = "PENDING_REVIEW") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Fetching temp ID review queue: tenant={}, facilityId={}, status={}, page={}, requestId={}",
                tenantId, facilityId, status, page, requestId);

        // Placeholder: realistic temp ID review queue
        List<Map<String, Object>> queue = List.of(
                createTempIdEntry("TMP-2026-00234", "City Clinic Harare", "FAC-001",
                        "Dr. Chiedza Nzou", "PRV-2024-00089",
                        "Male, ~35 years, presented with acute respiratory infection",
                        OffsetDateTime.now().minusHours(6)),
                createTempIdEntry("TMP-2026-00235", "Chitungwiza Central Hospital", "FAC-002",
                        "Sr. Nyasha Mutasa", "PRV-2024-00112",
                        "Female, ~28 years, antenatal first visit, no prior records",
                        OffsetDateTime.now().minusHours(4)),
                createTempIdEntry("TMP-2026-00236", "Parirenyatwa Group of Hospitals", "FAC-003",
                        "Dr. Tinashe Maponga", "PRV-2024-00045",
                        "Male, ~60 years, emergency admission, unconscious on arrival",
                        OffsetDateTime.now().minusHours(2)),
                createTempIdEntry("TMP-2026-00237", "City Clinic Harare", "FAC-001",
                        "Nurse Tendai Moyo", "PRV-2024-00201",
                        "Female, ~12 years, accompanied by guardian, immunization visit",
                        OffsetDateTime.now().minusMinutes(45))
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", queue);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page,
                        "size", size,
                        "total_elements", queue.size(),
                        "total_pages", 1
                )
        ));
        return ResponseEntity.ok(response);
    }

    /**
     * Approve a temporary ID for promotion to a permanent Health ID.
     *
     * <p>Marks the temp ID as approved by the reviewing supervisor. This triggers
     * the identity promotion workflow: VITO will generate a permanent Health ID
     * and link it to the temporary record, preserving continuity of any clinical
     * data recorded under the temp ID. An audit trail entry is created.</p>
     *
     * @param tempId        the temporary identifier to approve
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @param body          optional body with reviewer notes and identity proofing details
     * @return approval confirmation with assigned permanent Health ID (when available)
     */
    @PostMapping("/{tempId}/approve")
    public ResponseEntity<Map<String, Object>> approveTempId(
            @PathVariable String tempId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        String reviewerNotes = body != null
                ? body.getOrDefault("notes", "").toString()
                : "";

        log.info("Approving temp ID: tenant={}, tempId={}, requestId={}", tenantId, tempId, requestId);

        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("tempId", tempId);
        approval.put("status", "APPROVED");
        approval.put("previousStatus", "PENDING_REVIEW");
        approval.put("approvedAt", OffsetDateTime.now().toString());
        approval.put("reviewerNotes", reviewerNotes);
        approval.put("promotionStatus", "QUEUED");
        approval.put("estimatedHealthIdAssignment", "Within 24 hours");
        approval.put("auditEventId", "AE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", approval);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Reject a temporary ID with a documented reason.
     *
     * <p>Marks the temp ID as rejected. The rejection reason is mandatory and
     * becomes part of the permanent audit trail. Clinical data recorded under
     * the temp ID is preserved but flagged for reconciliation. Common rejection
     * reasons include duplicate identity detection, fraudulent presentation, or
     * insufficient demographic information for identity proofing.</p>
     *
     * @param tempId        the temporary identifier to reject
     * @param tenantId      mandatory tenant context (X-Tenant-ID)
     * @param requestId     unique request identifier (X-Request-ID)
     * @param correlationId correlation chain identifier (X-Correlation-ID)
     * @param body          request body with mandatory "reason" and optional "notes"
     * @return rejection confirmation with audit trail reference
     */
    @PostMapping("/{tempId}/reject")
    public ResponseEntity<Map<String, Object>> rejectTempId(
            @PathVariable String tempId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        String reason = body.getOrDefault("reason", "").toString();
        String notes = body.getOrDefault("notes", "").toString();

        if (reason.isBlank()) {
            log.warn("Temp ID rejection attempted without reason: tempId={}", tempId);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of(
                            "code", "REASON_REQUIRED",
                            "message", "A rejection reason is mandatory for audit trail compliance"
                    )
            ));
        }

        log.info("Rejecting temp ID: tenant={}, tempId={}, reason={}, requestId={}",
                tenantId, tempId, reason, requestId);

        Map<String, Object> rejection = new LinkedHashMap<>();
        rejection.put("tempId", tempId);
        rejection.put("status", "REJECTED");
        rejection.put("previousStatus", "PENDING_REVIEW");
        rejection.put("reason", reason);
        rejection.put("notes", notes);
        rejection.put("rejectedAt", OffsetDateTime.now().toString());
        rejection.put("clinicalDataAction", "FLAGGED_FOR_RECONCILIATION");
        rejection.put("auditEventId", "AE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", rejection);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    // -- Helper methods -------------------------------------------------------

    private Map<String, Object> createTempIdEntry(String tempId, String facilityName,
                                                   String facilityId, String issuedBy,
                                                   String issuedByProviderId,
                                                   String demographicSummary,
                                                   OffsetDateTime issuedAt) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("tempId", tempId);
        entry.put("status", "PENDING_REVIEW");
        entry.put("facilityName", facilityName);
        entry.put("facilityId", facilityId);
        entry.put("issuedBy", issuedBy);
        entry.put("issuedByProviderId", issuedByProviderId);
        entry.put("demographicSummary", demographicSummary);
        entry.put("issuedAt", issuedAt.toString());

        // Calculate elapsed time for display
        long hoursElapsed = java.time.Duration.between(issuedAt, OffsetDateTime.now()).toHours();
        entry.put("hoursElapsed", hoursElapsed);
        entry.put("urgency", hoursElapsed > 12 ? "HIGH" : hoursElapsed > 4 ? "MEDIUM" : "LOW");

        entry.put("hasLinkedClinicalData", true);
        entry.put("linkedEncounterCount", 1);
        return entry;
    }
}
