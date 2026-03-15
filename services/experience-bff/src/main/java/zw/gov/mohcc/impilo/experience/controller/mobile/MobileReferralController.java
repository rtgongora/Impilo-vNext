package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile referral endpoints.
 * POST /internal/v1/mobile/provider/referrals                          - create referral
 * GET  /internal/v1/mobile/provider/referrals?encounter_id= or patient_id= - list referrals
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/referrals")
public class MobileReferralController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public MobileReferralController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    public record CreateReferralRequest(
            @NotBlank String encounter_id,
            @NotBlank String patient_id,
            @NotBlank String referring_facility_id,
            @NotBlank String destination_facility_id,
            @NotBlank String referral_reason,
            String priority,
            String clinical_summary,
            String specialty
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
        String priority = request.priority() != null ? request.priority() : "ROUTINE";

        jdbcTemplate.update("""
            INSERT INTO referrals
                (id, tenant_id, encounter_id, patient_id, referring_facility_id,
                 destination_facility_id, referral_reason, priority, clinical_summary,
                 specialty, status, referred_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, 'PENDING', ?, ?, ?)
            """,
                referralId, tenantId, request.encounter_id(), request.patient_id(),
                request.referring_facility_id(), request.destination_facility_id(),
                request.referral_reason(), priority, request.clinical_summary(),
                request.specialty(), now, now, now);

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
                        "encounter_id", request.encounter_id(),
                        "patient_id", request.patient_id(),
                        "referring_facility_id", request.referring_facility_id(),
                        "destination_facility_id", request.destination_facility_id(),
                        "priority", priority,
                        "status", "PENDING"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("patient_id", request.patient_id());
        attributes.put("referring_facility_id", request.referring_facility_id());
        attributes.put("destination_facility_id", request.destination_facility_id());
        attributes.put("referral_reason", request.referral_reason());
        attributes.put("priority", priority);
        attributes.put("clinical_summary", request.clinical_summary());
        attributes.put("specialty", request.specialty());
        attributes.put("status", "PENDING");
        attributes.put("referred_at", now);
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

        if (encounterId != null) {
            rows = jdbcTemplate.queryForList("""
                SELECT id, encounter_id, patient_id, referring_facility_id, destination_facility_id,
                       referral_reason, priority, clinical_summary, specialty, status,
                       referred_at, accepted_at, completed_at, created_at, updated_at
                FROM referrals
                WHERE tenant_id = ? AND encounter_id = ?::uuid
                ORDER BY referred_at DESC
                LIMIT ? OFFSET ?
                """, tenantId, encounterId, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM referrals WHERE tenant_id = ? AND encounter_id = ?::uuid
                """, Long.class, tenantId, encounterId);
        } else if (patientId != null) {
            rows = jdbcTemplate.queryForList("""
                SELECT id, encounter_id, patient_id, referring_facility_id, destination_facility_id,
                       referral_reason, priority, clinical_summary, specialty, status,
                       referred_at, accepted_at, completed_at, created_at, updated_at
                FROM referrals
                WHERE tenant_id = ? AND patient_id = ?::uuid
                ORDER BY referred_at DESC
                LIMIT ? OFFSET ?
                """, tenantId, patientId, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM referrals WHERE tenant_id = ? AND patient_id = ?::uuid
                """, Long.class, tenantId, patientId);
        } else {
            rows = List.of();
            total = 0L;
        }

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("encounter_id", row.get("encounter_id"));
            attributes.put("patient_id", row.get("patient_id"));
            attributes.put("referring_facility_id", row.get("referring_facility_id"));
            attributes.put("destination_facility_id", row.get("destination_facility_id"));
            attributes.put("referral_reason", row.get("referral_reason"));
            attributes.put("priority", row.get("priority"));
            attributes.put("clinical_summary", row.get("clinical_summary"));
            attributes.put("specialty", row.get("specialty"));
            attributes.put("status", row.get("status"));
            attributes.put("referred_at", row.get("referred_at"));
            attributes.put("accepted_at", row.get("accepted_at"));
            attributes.put("completed_at", row.get("completed_at"));
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
