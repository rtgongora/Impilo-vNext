package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Mobile provider triage endpoints.
 *
 * <p>POST /internal/v1/mobile/provider/triage — record triage from mobile</p>
 * <p>GET  /internal/v1/mobile/provider/triage?encounter_id= or patient_id= — list triage records</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/triage")
public class MobileTriageController {

    private static final Logger log = LoggerFactory.getLogger(MobileTriageController.class);

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;
    private final PctServiceClient pctClient;

    public MobileTriageController(JdbcTemplate jdbcTemplate,
                                  OutboxService outboxService,
                                  PctServiceClient pctClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
        this.pctClient = pctClient;
    }

    public record RecordTriageRequest(
            @NotBlank String patient_id,
            String encounter_id,
            String queue_entry_id,
            @Min(1) @Max(5) int acuity,
            String chief_complaint,
            List<Map<String, Object>> danger_signs,
            Map<String, Object> vitals,
            String notes,
            @NotBlank String triaged_by,
            String triaged_by_name
    ) {}

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> recordTriage(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RecordTriageRequest request) {

        UUID triageId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        String dangerSignsJson = "[]";
        String vitalsJson = null;
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (request.danger_signs() != null) dangerSignsJson = mapper.writeValueAsString(request.danger_signs());
            if (request.vitals() != null) vitalsJson = mapper.writeValueAsString(request.vitals());
        } catch (Exception e) {
            log.warn("Failed to serialize triage data: {}", e.getMessage());
        }

        // Resolve PCT journey
        String pctJourneyId = null;
        if (request.encounter_id() != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT pct_journey_id FROM encounters WHERE id = ?::uuid AND tenant_id = ?",
                    request.encounter_id(), tenantId);
            if (!rows.isEmpty() && rows.get(0).get("pct_journey_id") != null) {
                pctJourneyId = rows.get(0).get("pct_journey_id").toString();
            }
        }

        jdbcTemplate.update("""
            INSERT INTO triage_records
                (id, tenant_id, patient_id, encounter_id, queue_entry_id, pct_journey_id,
                 acuity, chief_complaint, danger_signs, vitals, notes,
                 triaged_by, triaged_by_name, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
            """,
                triageId, tenantId, request.patient_id(), request.encounter_id(),
                request.queue_entry_id(), pctJourneyId,
                request.acuity(), request.chief_complaint(),
                dangerSignsJson, vitalsJson, request.notes(),
                request.triaged_by(), request.triaged_by_name(),
                now, now);

        // PCT delegation
        if (pctJourneyId != null) {
            try {
                pctClient.recordTriage(pctJourneyId, String.valueOf(request.acuity()), null, request.notes());
                log.info("PCT triage delegated from mobile for journey={}", pctJourneyId);
            } catch (Exception e) {
                log.warn("PCT triage delegation from mobile failed (non-blocking): {}", e.getMessage());
            }
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.triage.recorded.v1",
                correlationId, requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId, podId,
                "TriageRecord", triageId.toString(),
                Map.of("triage_id", triageId.toString(), "patient_id", request.patient_id(), "acuity", request.acuity()),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", triageId.toString(), "type", "TriageRecord",
                "attributes", Map.of("acuity", request.acuity(), "status", "RECORDED")));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listTriage(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "encounter_id") String encounterId,
            @RequestParam(required = false, name = "patient_id") String patientId) {

        List<Map<String, Object>> rows;
        if (encounterId != null) {
            rows = jdbcTemplate.queryForList("""
                SELECT id, acuity, chief_complaint, danger_signs, vitals, notes,
                       triaged_by, triaged_by_name, created_at
                FROM triage_records WHERE tenant_id = ? AND encounter_id = ?::uuid
                ORDER BY created_at DESC
                """, tenantId, encounterId);
        } else if (patientId != null) {
            rows = jdbcTemplate.queryForList("""
                SELECT id, acuity, chief_complaint, danger_signs, vitals, notes,
                       triaged_by, triaged_by_name, created_at
                FROM triage_records WHERE tenant_id = ? AND patient_id = ?::uuid
                ORDER BY created_at DESC
                """, tenantId, patientId);
        } else {
            rows = List.of();
        }

        List<Map<String, Object>> data = rows.stream().map(row -> Map.<String, Object>of(
                "id", row.get("id").toString(), "type", "TriageRecord", "attributes", row
        )).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
