package zw.gov.mohcc.impilo.experience.controller.mobile;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile telemedicine session endpoints.
 * GET  /internal/v1/mobile/provider/telemedicine/sessions             - list sessions
 * POST /internal/v1/mobile/provider/telemedicine/sessions/{id}/join   - join session
 * POST /internal/v1/mobile/provider/telemedicine/sessions/{id}/end    - end session
 *
 * <p>STRANGLER: JdbcTemplate retained for local reads during migration; writes delegated to PctServiceClient.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/telemedicine")
public class MobileTelemedicineController {

    private final PctServiceClient pctClient;

        this.pctClient = pctClient;
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> listSessions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "provider_id") String providerId,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(required = false, name = "facility_id") String facilityId,
            @RequestParam(required = false, name = "referral_id") String referralId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    /**
     * Create a new telemedicine session, optionally linked to a referral.
     * POST /internal/v1/mobile/provider/telemedicine/sessions
     */
    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {

        UUID sessionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        String sessionType = body.getOrDefault("session_type", "VIDEO").toString();
        String patientId = body.get("patient_id") != null ? body.get("patient_id").toString() : null;
        String providerId = body.get("provider_id") != null ? body.get("provider_id").toString() : null;
        String facilityId = body.get("facility_id") != null ? body.get("facility_id").toString() : null;
        String encounterId = body.get("encounter_id") != null ? body.get("encounter_id").toString() : null;
        String referralId = body.get("referral_id") != null ? body.get("referral_id").toString() : null;
        String scheduledAt = body.get("scheduled_at") != null ? body.get("scheduled_at").toString() : null;
        String notes = body.get("notes") != null ? body.get("notes").toString() : null;

        OffsetDateTime scheduled = scheduledAt != null
                ? OffsetDateTime.parse(scheduledAt)
                : now.plusHours(1);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("session_type", sessionType);
        attributes.put("status", "SCHEDULED");
        attributes.put("patient_id", patientId);
        attributes.put("provider_id", providerId);
        attributes.put("facility_id", facilityId);
        attributes.put("encounter_id", encounterId);
        attributes.put("referral_id", referralId);
        attributes.put("scheduled_at", scheduled);
        attributes.put("room_url", "session-" + sessionId);
        attributes.put("notes", notes);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", sessionId.toString(),
                "type", "TelemedicineSession",
                "attributes", attributes
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/sessions/{id}/join")
    public ResponseEntity<Map<String, Object>> joinSession(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @PostMapping("/sessions/{id}/end")
    public ResponseEntity<Map<String, Object>> endSession(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    private Map<String, Object> toResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter_id", row.get("encounter_id"));
        attributes.put("patient_id", row.get("patient_id"));
        attributes.put("provider_id", row.get("provider_id"));
        attributes.put("facility_id", row.get("facility_id"));
        attributes.put("session_type", row.get("session_type"));
        attributes.put("status", row.get("status"));
        attributes.put("room_url", row.get("room_url"));
        attributes.put("scheduled_at", row.get("scheduled_at"));
        attributes.put("started_at", row.get("started_at"));
        attributes.put("ended_at", row.get("ended_at"));
        attributes.put("duration_seconds", row.get("duration_seconds"));
        attributes.put("notes", row.get("notes"));
        attributes.put("referral_id", row.get("referral_id"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "TelemedicineSession");
        resource.put("attributes", attributes);
        return resource;
    }
}
