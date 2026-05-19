package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mobile referral endpoints.
 * POST /internal/v1/mobile/provider/referrals                          - create referral
 * GET  /internal/v1/mobile/provider/referrals?encounter_id= or patient_id= - list referrals
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/referrals")
public class MobileReferralController {

    private final PctServiceClient pctClient;

    public MobileReferralController(PctServiceClient pctClient) {
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

        String urgency = request.urgency() != null ? request.urgency() : "ROUTINE";
        String referralType = request.referral_type() != null ? request.referral_type() : "SPECIALIST";

        try {
            Map<String, Object> pctBody = new LinkedHashMap<>();
            pctBody.put("patientId", request.patient_id());
            pctBody.put("referralType", referralType);
            pctBody.put("reason", request.reason());
            pctBody.put("urgency", urgency);
            pctBody.put("referredToFacility", request.referred_to_facility());
            pctBody.put("referredBy", request.referred_by());
            if (request.encounter_id() != null) pctBody.put("encounterId", request.encounter_id());
            if (request.specialty() != null) pctBody.put("specialty", request.specialty());
            if (request.clinical_summary() != null) pctBody.put("clinicalSummary", request.clinical_summary());
            JsonNode pctResult = pctClient.createReferral(pctBody);
            if (pctResult == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No referral payload returned", requestId, correlationId);
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", pctResult,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ex) {
            return upstreamFailure("PCT_UNAVAILABLE", ex.getMessage(), requestId, correlationId);
        }
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

        try {
            JsonNode result = pctClient.acceptReferral(id.toString(), body);
            if (result != null) {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("data", result);
                response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                return ResponseEntity.ok(response);
            }
            return upstreamFailure("PCT_UNAVAILABLE", "No accept payload returned", requestId, correlationId);
        } catch (Exception ex) {
            return upstreamFailure("PCT_UNAVAILABLE", ex.getMessage(), requestId, correlationId);
        }
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

        String responseNotes = body != null ? (String) body.get("response_notes") : null;
        if (responseNotes == null || responseNotes.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "response_notes is required")));
        }

        try {
            JsonNode result = pctClient.respondReferral(id.toString(), body);
            if (result == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No response payload returned", requestId, correlationId);
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", result);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            return upstreamFailure("PCT_UNAVAILABLE", ex.getMessage(), requestId, correlationId);
        }
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
        if (patientId != null && !patientId.isBlank()) {
            try {
                JsonNode data = pctClient.listPatientReferrals(patientId, page, size);
                if (data != null) {
                    return ResponseEntity.ok(Map.of(
                            "data", data,
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                    ));
                }
                return upstreamFailure("PCT_UNAVAILABLE", "No referrals payload returned", requestId, correlationId);
            } catch (Exception ex) {
                return upstreamFailure("PCT_UNAVAILABLE", ex.getMessage(), requestId, correlationId);
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", Map.of("code", "MISSING_FILTER", "message", "patient_id is required"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Referral upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
