package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

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

    private final PctServiceClient pctClient;
    private final CostaServiceClient costaClient;

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
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    /**
     * Create a new encounter with PCT delegation.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createEncounter(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateEncounterRequest request) {

        UUID encounterId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        // Delegate to PCT
        String pctEncounterRef = null;
        if (request.pct_journey_id() != null && !request.pct_journey_id().isBlank()) {
            try {
                JsonNode pctEncounter = pctClient.startEncounter(
                        request.pct_journey_id(), request.encounter_type());
                if (pctEncounter != null && pctEncounter.has("encounterRef")) {
                    pctEncounterRef = pctEncounter.get("encounterRef").asText();
                }
                log.info("PCT encounter started from mobile: ref={}", pctEncounterRef);
            } catch (Exception e) {
                log.warn("PCT encounter delegation from mobile failed (non-blocking): {}", e.getMessage());
            }
        }

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
    public ResponseEntity<Map<String, Object>> closeEncounter(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        OffsetDateTime now = OffsetDateTime.now();

        if (updated == 0) {
            throw new ResourceNotFoundException("Active encounter not found: " + id);
        }

        // Complete queue entries
        try {
            if (!encRows.isEmpty()) {
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
                log.info("COSTA bill draft created from mobile close: billId={} for encounter={}", costaBillId, id);
            }
        } catch (Exception e) {
            log.warn("COSTA bill draft creation from mobile close failed (non-blocking): {}", e.getMessage());
        }

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("encounter_id", id.toString());
        eventPayload.put("status", "COMPLETED");
        if (costaBillId != null) eventPayload.put("costa_bill_id", costaBillId);

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

        if (encRows.isEmpty()) {
            throw new ResourceNotFoundException("Encounter not found: " + id);
        }

        Map<String, Object> encounter = encRows.get(0);
        Object patientId = encounter.get("patient_id");

        // Aggregate clinical context

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
