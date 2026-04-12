package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

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

    // STRANGLER: JdbcTemplate retained for local reads during migration; writes delegated to PctServiceClient
    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;
    private final PctServiceClient pctClient;

    public MobileReferralController(JdbcTemplate jdbcTemplate, OutboxService outboxService,
                                    PctServiceClient pctClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
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
    @Transactional
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

        jdbcTemplate.update("""
            INSERT INTO referrals
                (id, tenant_id, patient_id, encounter_id, referral_type,
                 specialty, referred_to, referred_to_facility, reason, urgency,
                 status, clinical_summary, referred_by, referred_by_name,
                 created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)
            """,
                referralId, tenantId, request.patient_id(), request.encounter_id(),
                referralType, request.specialty(), request.referred_to(),
                request.referred_to_facility(), request.reason(), urgency,
                request.clinical_summary(),
                request.referred_by(), request.referred_by_name() != null ? request.referred_by_name() : "",
                now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.referral.created.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "Referral",
                referralId.toString(),
                Map.of(
                        "referral_id", referralId.toString(),
                        "patient_id", request.patient_id(),
                        "referred_to_facility", request.referred_to_facility(),
                        "referred_by", request.referred_by(),
                        "urgency", urgency,
                        "status", "PENDING"
                ),
                Map.of()
        );

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
    @Transactional
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

        int updated = jdbcTemplate.update("""
            UPDATE referrals SET status = 'ACCEPTED',
                receiving_facility_id = COALESCE(?::uuid, receiving_facility_id),
                receiving_facility_name = COALESCE(?, receiving_facility_name),
                accepted_at = ?, updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status = 'PENDING'
            """, facilityId, facilityName, now, now, id, tenantId);

        if (updated == 0) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND", "message", "Pending referral not found: " + id)));
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.referral.accepted.v1",
                correlationId, requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId, podId,
                "Referral", id.toString(),
                Map.of("referral_id", id.toString(), "status", "ACCEPTED"),
                Map.of()
        );

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
    @Transactional
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

        int updated = jdbcTemplate.update("""
            UPDATE referrals SET status = 'RESPONDED',
                response_notes = ?, outcome = COALESCE(?, outcome),
                responded_at = ?, updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status IN ('PENDING', 'ACCEPTED')
            """, responseNotes, outcome, now, now, id, tenantId);

        if (updated == 0) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "NOT_FOUND", "message", "Actionable referral not found: " + id)));
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.referral.responded.v1",
                correlationId, requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId, podId,
                "Referral", id.toString(),
                Map.of("referral_id", id.toString(), "status", "RESPONDED",
                        "outcome", outcome != null ? outcome : ""),
                Map.of()
        );

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

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows;
        Long total;

        String selectCols = """
                SELECT id, patient_id, encounter_id, referral_type, specialty,
                       referred_to, referred_to_facility, reason, urgency, status,
                       clinical_summary, referred_by, referred_by_name,
                       receiving_facility_id, receiving_facility_name,
                       response_notes, responded_at, accepted_at,
                       scheduled_at, completed_at, outcome, created_at, updated_at
                FROM referrals
                """;

        if (encounterId != null) {
            rows = jdbcTemplate.queryForList(
                    selectCols + " WHERE tenant_id = ? AND encounter_id = ?::uuid ORDER BY created_at DESC LIMIT ? OFFSET ?",
                    tenantId, encounterId, limit, offset);
            total = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM referrals WHERE tenant_id = ? AND encounter_id = ?::uuid",
                    Long.class, tenantId, encounterId);
        } else if (patientId != null) {
            rows = jdbcTemplate.queryForList(
                    selectCols + " WHERE tenant_id = ? AND patient_id = ?::uuid ORDER BY created_at DESC LIMIT ? OFFSET ?",
                    tenantId, patientId, limit, offset);
            total = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM referrals WHERE tenant_id = ? AND patient_id = ?::uuid",
                    Long.class, tenantId, patientId);
        } else {
            rows = List.of();
            total = 0L;
        }

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("patient_id", row.get("patient_id"));
            attributes.put("encounter_id", row.get("encounter_id"));
            attributes.put("referral_type", row.get("referral_type"));
            attributes.put("specialty", row.get("specialty"));
            attributes.put("referred_to", row.get("referred_to"));
            attributes.put("referred_to_facility", row.get("referred_to_facility"));
            attributes.put("reason", row.get("reason"));
            attributes.put("urgency", row.get("urgency"));
            attributes.put("status", row.get("status"));
            attributes.put("clinical_summary", row.get("clinical_summary"));
            attributes.put("referred_by", row.get("referred_by"));
            attributes.put("referred_by_name", row.get("referred_by_name"));
            attributes.put("receiving_facility_id", row.get("receiving_facility_id"));
            attributes.put("receiving_facility_name", row.get("receiving_facility_name"));
            attributes.put("response_notes", row.get("response_notes"));
            attributes.put("responded_at", row.get("responded_at"));
            attributes.put("accepted_at", row.get("accepted_at"));
            attributes.put("outcome", row.get("outcome"));
            attributes.put("created_at", row.get("created_at"));
            attributes.put("updated_at", row.get("updated_at"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "Referral");
            resource.put("attributes", attributes);
            return resource;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page,
                        "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0
                )
        ));

        return ResponseEntity.ok(response);
    }
}
