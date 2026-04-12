package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Triage assessment endpoints.
 *
 * <p>Records triage assessments with acuity (1-5), danger signs, vitals,
 * and clinical notes. Persists locally in the BFF for encounter workspace
 * access and delegates to PCT TriageService for sovereign journey lifecycle.</p>
 *
 * <p>Acuity scale (South African Triage Scale aligned):</p>
 * <ul>
 *   <li>1 = Red / Resuscitation — immediate</li>
 *   <li>2 = Orange / Emergency — very urgent (≤10 min)</li>
 *   <li>3 = Yellow / Urgent — urgent (≤60 min)</li>
 *   <li>4 = Green / Standard — less urgent (≤240 min)</li>
 *   <li>5 = Blue / Non-urgent — routine</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/triage")
public class TriageController {

    private static final Logger log = LoggerFactory.getLogger(TriageController.class);

    private final JdbcTemplate jdbcTemplate; // TODO: remove after verification
    private final OutboxService outboxService;
    private final PctServiceClient pctClient;

    public TriageController(JdbcTemplate jdbcTemplate,
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
            List<DangerSign> danger_signs,
            Map<String, Object> vitals,
            String notes,
            @NotBlank String triaged_by,
            String triaged_by_name
    ) {}

    public record DangerSign(
            String name,
            boolean present
    ) {}

    /**
     * Record a triage assessment.
     * POST /internal/v1/triage
     */
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
            if (request.danger_signs() != null) {
                dangerSignsJson = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(request.danger_signs());
            }
            if (request.vitals() != null) {
                vitalsJson = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(request.vitals());
            }
        } catch (Exception e) {
            log.warn("Failed to serialize triage data: {}", e.getMessage());
        }

        // Resolve PCT journey ID from queue entry or encounter
        String pctJourneyId = null;
        if (request.queue_entry_id() != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT pct_journey_id FROM queue_entries WHERE id = ?::uuid AND tenant_id = ?",
                    request.queue_entry_id(), tenantId);
            if (!rows.isEmpty() && rows.get(0).get("pct_journey_id") != null) {
                pctJourneyId = rows.get(0).get("pct_journey_id").toString();
            }
        }
        if (pctJourneyId == null && request.encounter_id() != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT pct_journey_id FROM encounters WHERE id = ?::uuid AND tenant_id = ?",
                    request.encounter_id(), tenantId);
            if (!rows.isEmpty() && rows.get(0).get("pct_journey_id") != null) {
                pctJourneyId = rows.get(0).get("pct_journey_id").toString();
            }
        }

        // Persist locally
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

        // Delegate to PCT sovereign service
        if (pctJourneyId != null) {
            try {
                pctClient.recordTriage(pctJourneyId, String.valueOf(request.acuity()),
                        vitalsJson != null ? Map.of("raw", vitalsJson) : null, request.notes());
                log.info("PCT triage delegated for journey={}, acuity={}", pctJourneyId, request.acuity());
            } catch (Exception e) {
                log.warn("PCT triage delegation failed (non-blocking): {}", e.getMessage());
            }
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.triage.recorded.v1",
                correlationId, requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId, podId,
                "TriageRecord", triageId.toString(),
                Map.of(
                        "triage_id", triageId.toString(),
                        "patient_id", request.patient_id(),
                        "acuity", request.acuity(),
                        "status", "RECORDED"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("acuity", request.acuity());
        attributes.put("chief_complaint", request.chief_complaint());
        attributes.put("danger_signs", request.danger_signs());
        attributes.put("vitals", request.vitals());
        attributes.put("notes", request.notes());
        attributes.put("triaged_by", request.triaged_by());
        attributes.put("triaged_by_name", request.triaged_by_name());
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", triageId.toString(),
                "type", "TriageRecord",
                "attributes", attributes
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get triage records for a patient or encounter.
     * GET /internal/v1/triage?patient_id= or encounter_id=
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listTriageRecords(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(required = false, name = "encounter_id") String encounterId) {

        List<Map<String, Object>> rows;
        if (encounterId != null) {
            rows = jdbcTemplate.queryForList("""
                SELECT id, patient_id, encounter_id, queue_entry_id, pct_journey_id,
                       acuity, chief_complaint, danger_signs, vitals, notes,
                       triaged_by, triaged_by_name, created_at, updated_at
                FROM triage_records
                WHERE tenant_id = ? AND encounter_id = ?::uuid
                ORDER BY created_at DESC
                """, tenantId, encounterId);
        } else if (patientId != null) {
            rows = jdbcTemplate.queryForList("""
                SELECT id, patient_id, encounter_id, queue_entry_id, pct_journey_id,
                       acuity, chief_complaint, danger_signs, vitals, notes,
                       triaged_by, triaged_by_name, created_at, updated_at
                FROM triage_records
                WHERE tenant_id = ? AND patient_id = ?::uuid
                ORDER BY created_at DESC
                """, tenantId, patientId);
        } else {
            rows = List.of();
        }

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("patient_id", row.get("patient_id"));
            attributes.put("encounter_id", row.get("encounter_id"));
            attributes.put("acuity", row.get("acuity"));
            attributes.put("chief_complaint", row.get("chief_complaint"));
            attributes.put("danger_signs", row.get("danger_signs"));
            attributes.put("vitals", row.get("vitals"));
            attributes.put("notes", row.get("notes"));
            attributes.put("triaged_by", row.get("triaged_by"));
            attributes.put("triaged_by_name", row.get("triaged_by_name"));
            attributes.put("created_at", row.get("created_at"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "TriageRecord");
            resource.put("attributes", attributes);
            return resource;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));

        return ResponseEntity.ok(response);
    }
}
