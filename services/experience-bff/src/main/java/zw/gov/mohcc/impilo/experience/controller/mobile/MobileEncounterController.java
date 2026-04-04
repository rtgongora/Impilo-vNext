package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile provider encounter endpoints.
 *
 * <p>Provides encounter lifecycle operations for mobile provider apps,
 * with PCT sovereign service delegation for journey/encounter management.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET  /internal/v1/mobile/provider/encounters?patient_id= — list encounters</li>
 *   <li>POST /internal/v1/mobile/provider/encounters — create encounter</li>
 *   <li>POST /internal/v1/mobile/provider/encounters/{id}/close — close encounter</li>
 *   <li>GET  /internal/v1/mobile/provider/encounters/{id}/summary — patient encounter summary</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/encounters")
public class MobileEncounterController {

    private static final Logger log = LoggerFactory.getLogger(MobileEncounterController.class);

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;
    private final PctServiceClient pctClient;
    private final CostaServiceClient costaClient;

    public MobileEncounterController(JdbcTemplate jdbcTemplate,
                                     OutboxService outboxService,
                                     PctServiceClient pctClient,
                                     CostaServiceClient costaClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
        this.pctClient = pctClient;
        this.costaClient = costaClient;
    }

    public record CreateEncounterRequest(
            @NotBlank String patient_id,
            @NotBlank String facility_id,
            @NotBlank String encounter_type,
            String chief_complaint,
            String pct_journey_id,
            String patient_cpid
    ) {}

    /**
     * List encounters for a patient.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listEncounters(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, patient_id, facility_id, encounter_type, status,
                   chief_complaint, started_at, ended_at, pct_journey_id,
                   pct_encounter_ref, created_at, updated_at
            FROM encounters
            WHERE tenant_id = ? AND patient_id = ?::uuid
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """, tenantId, patientId, limit, offset);

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("patient_id", row.get("patient_id"));
            attributes.put("facility_id", row.get("facility_id"));
            attributes.put("encounter_type", row.get("encounter_type"));
            attributes.put("status", row.get("status"));
            attributes.put("chief_complaint", row.get("chief_complaint"));
            attributes.put("started_at", row.get("started_at"));
            attributes.put("ended_at", row.get("ended_at"));
            attributes.put("pct_journey_id", row.get("pct_journey_id"));
            attributes.put("created_at", row.get("created_at"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "Encounter");
            resource.put("attributes", attributes);
            return resource;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Create a new encounter with PCT delegation.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createEncounter(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateEncounterRequest request) {

        UUID encounterId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO encounters
                (id, tenant_id, facility_id, patient_id, encounter_type,
                 chief_complaint, status, started_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, 'IN_PROGRESS', ?, ?, ?)
            """,
                encounterId, tenantId, request.facility_id(), request.patient_id(),
                request.encounter_type(), request.chief_complaint(),
                now, now, now);

        // Delegate to PCT
        String pctEncounterRef = null;
        if (request.pct_journey_id() != null && !request.pct_journey_id().isBlank()) {
            try {
                JsonNode pctEncounter = pctClient.startEncounter(
                        request.pct_journey_id(), request.encounter_type());
                if (pctEncounter != null && pctEncounter.has("encounterRef")) {
                    pctEncounterRef = pctEncounter.get("encounterRef").asText();
                }
                jdbcTemplate.update("""
                    UPDATE encounters SET pct_encounter_ref = ?, pct_journey_id = ?
                    WHERE id = ? AND tenant_id = ?
                    """, pctEncounterRef, request.pct_journey_id(), encounterId, tenantId);
                log.info("PCT encounter started from mobile: ref={}", pctEncounterRef);
            } catch (Exception e) {
                log.warn("PCT encounter delegation from mobile failed (non-blocking): {}", e.getMessage());
            }
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.encounter.created.v1",
                correlationId, requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId, podId,
                "Encounter", encounterId.toString(),
                Map.of(
                        "encounter_id", encounterId.toString(),
                        "patient_id", request.patient_id(),
                        "encounter_type", request.encounter_type(),
                        "status", "IN_PROGRESS"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("facility_id", request.facility_id());
        attributes.put("encounter_type", request.encounter_type());
        attributes.put("chief_complaint", request.chief_complaint());
        attributes.put("status", "IN_PROGRESS");
        attributes.put("started_at", now);
        if (pctEncounterRef != null) {
            attributes.put("pct_encounter_ref", pctEncounterRef);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", encounterId.toString(), "type", "Encounter", "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Close an encounter with PCT delegation.
     */
    @PostMapping("/{id}/close")
    @Transactional
    public ResponseEntity<Map<String, Object>> closeEncounter(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        OffsetDateTime now = OffsetDateTime.now();

        int updated = jdbcTemplate.update("""
            UPDATE encounters SET status = 'COMPLETED', ended_at = ?, updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status IN ('IN_PROGRESS', 'ACTIVE')
            """, now, now, id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Active encounter not found: " + id);
        }

        // Complete queue entries
        try {
            List<Map<String, Object>> encRows = jdbcTemplate.queryForList(
                    "SELECT patient_id, facility_id FROM encounters WHERE id = ? AND tenant_id = ?", id, tenantId);
            if (!encRows.isEmpty()) {
                jdbcTemplate.update("""
                    UPDATE queue_entries SET status = 'COMPLETED', completed_at = ?, updated_at = ?
                    WHERE tenant_id = ? AND patient_id = ? AND facility_id = ?
                    AND status IN ('CALLED', 'WAITING', 'SEEN')
                    """, now, now, tenantId, encRows.get(0).get("patient_id"), encRows.get(0).get("facility_id"));
            }
        } catch (Exception e) {
            log.warn("Queue completion from mobile close failed (non-blocking): {}", e.getMessage());
        }

        // Delegate to COSTA: create bill draft for the closed encounter
        String costaBillId = null;
        try {
            JsonNode billData = costaClient.createBillDraft(id.toString(), "ENCOUNTER");
            if (billData != null && billData.has("billId")) {
                costaBillId = billData.get("billId").asText();
                jdbcTemplate.update(
                        "UPDATE encounters SET costa_bill_id = ? WHERE id = ? AND tenant_id = ?",
                        costaBillId, id, tenantId);
                log.info("COSTA bill draft created from mobile close: billId={} for encounter={}", costaBillId, id);
            }
        } catch (Exception e) {
            log.warn("COSTA bill draft creation from mobile close failed (non-blocking): {}", e.getMessage());
        }

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("encounter_id", id.toString());
        eventPayload.put("status", "COMPLETED");
        if (costaBillId != null) eventPayload.put("costa_bill_id", costaBillId);

        outboxService.writeOutboxEvent(
                "impilo.experience.encounter.closed.v1",
                correlationId, requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId, podId,
                "Encounter", id.toString(),
                eventPayload,
                Map.of()
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id.toString());
        data.put("status", "COMPLETED");
        if (costaBillId != null) data.put("costa_bill_id", costaBillId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Get encounter summary — aggregated patient context for mobile.
     * Includes active encounter, recent vitals, active conditions, allergies, medications.
     */
    @GetMapping("/{id}/summary")
    public ResponseEntity<Map<String, Object>> getEncounterSummary(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> encRows = jdbcTemplate.queryForList(
                "SELECT * FROM encounters WHERE id = ? AND tenant_id = ?", id, tenantId);
        if (encRows.isEmpty()) {
            throw new ResourceNotFoundException("Encounter not found: " + id);
        }

        Map<String, Object> encounter = encRows.get(0);
        Object patientId = encounter.get("patient_id");

        // Aggregate clinical context
        List<Map<String, Object>> allergies = jdbcTemplate.queryForList(
                "SELECT allergen, severity, status FROM allergies WHERE tenant_id = ? AND patient_id = ? AND status = 'ACTIVE'",
                tenantId, patientId);
        List<Map<String, Object>> conditions = jdbcTemplate.queryForList(
                "SELECT condition_name, severity, clinical_status FROM conditions WHERE tenant_id = ? AND patient_id = ? AND clinical_status = 'ACTIVE'",
                tenantId, patientId);
        List<Map<String, Object>> medications = jdbcTemplate.queryForList(
                "SELECT medication_name, dosage, frequency, status FROM prescriptions WHERE tenant_id = ? AND patient_id = ? AND status IN ('PENDING', 'ACTIVE') ORDER BY created_at DESC LIMIT 10",
                tenantId, patientId);
        List<Map<String, Object>> recentVitals = jdbcTemplate.queryForList(
                "SELECT systolic, diastolic, heart_rate, temperature, oxygen_saturation, respiratory_rate, created_at FROM vitals_records WHERE tenant_id = ? AND patient_id = ? ORDER BY created_at DESC LIMIT 1",
                tenantId, patientId);
        List<Map<String, Object>> triage = jdbcTemplate.queryForList(
                "SELECT acuity, danger_signs, notes, created_at FROM triage_records WHERE tenant_id = ? AND encounter_id = ? ORDER BY created_at DESC LIMIT 1",
                tenantId, id);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("encounter", encounter);
        summary.put("allergies", allergies);
        summary.put("active_conditions", conditions);
        summary.put("current_medications", medications);
        summary.put("latest_vitals", recentVitals.isEmpty() ? null : recentVitals.get(0));
        summary.put("triage", triage.isEmpty() ? null : triage.get(0));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", summary);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
