package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile referral endpoints.
 * POST /internal/v1/mobile/provider/referrals                          - create referral
 * GET  /internal/v1/mobile/provider/referrals?encounter_id= or patient_id= - list referrals
 *
 * <p>STRANGLER: JdbcTemplate retained for local reads during migration; writes delegated to PctServiceClient.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/referrals")
public class MobileReferralController {

    private final PctServiceClient pctClient;

        this.pctClient = pctClient;
    }

    /**
     * Mobile referral creation request.
     * Field names match the canonical referral schema (V6):
     *   reason (not referral_reason), urgency (not priority),
     *   referred_to_facility (not destination_facility_id),
     *   referred_by (not referring_facility_id).
     */
    public record CreateReferralRequest(
            @NotBlank String patient_id,
            String encounter_id,
            String referral_type,
            String specialty,
            String referred_to,
            @NotBlank String referred_to_facility,
            @NotBlank String reason,
            String urgency,
            String clinical_summary,
            @NotBlank String referred_by,
            String referred_by_name
    ) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> createReferral(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateReferralRequest request) {

        UUID referralId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String urgency = request.urgency() != null ? request.urgency() : "ROUTINE";
        String referralType = request.referral_type() != null ? request.referral_type() : "SPECIALIST";

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("referral_type", referralType);
        attributes.put("specialty", request.specialty());
        attributes.put("referred_to", request.referred_to());
        attributes.put("referred_to_facility", request.referred_to_facility());
        attributes.put("reason", request.reason());
        attributes.put("urgency", urgency);
        attributes.put("clinical_summary", request.clinical_summary());
        attributes.put("referred_by", request.referred_by());
        attributes.put("referred_by_name", request.referred_by_name());
        attributes.put("status", "PENDING");
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", referralId.toString(),
                "type", "Referral",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Accept a referral from the mobile provider app.
     * POST /internal/v1/mobile/provider/referrals/{id}/accept
     */
    @PostMapping("/{id}/accept")
    public ResponseEntity<Map<String, Object>> acceptReferral(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        OffsetDateTime now = OffsetDateTime.now();
        String facilityId = body != null ? (String) body.get("facility_id") : null;
        String facilityName = body != null ? (String) body.get("facility_name") : null;

        if (updated == 0) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND", "message", "Pending referral not found: " + id)));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "status", "ACCEPTED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Respond to a referral from the mobile provider app.
     * POST /internal/v1/mobile/provider/referrals/{id}/respond
     */
    @PostMapping("/{id}/respond")
    public ResponseEntity<Map<String, Object>> respondReferral(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {

        OffsetDateTime now = OffsetDateTime.now();
        String responseNotes = body != null ? (String) body.get("response_notes") : null;
        String outcome = body != null ? (String) body.get("outcome") : null;

        if (responseNotes == null || responseNotes.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "response_notes is required")));
        }

        if (updated == 0) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND", "message", "Actionable referral not found: " + id)));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "status", "RESPONDED", "outcome", outcome));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listReferrals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "encounter_id") String encounterId,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }
}
