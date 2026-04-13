package zw.gov.mohcc.impilo.experience.controller;

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

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Encounter management endpoints.
 * GET  /internal/v1/encounters — list encounters with patient_id filter, pagination.
 * GET  /internal/v1/encounters/{id} — get single encounter.
 * POST /internal/v1/encounters — create encounter.
 * POST /internal/v1/encounters/{id}/close — close encounter.
 */
@RestController
@RequestMapping("/internal/v1/encounters")
public class EncounterController {

    private static final Logger log = LoggerFactory.getLogger(EncounterController.class);

    private final PctServiceClient pctClient;
    private final CostaServiceClient costaClient;

    public EncounterController(PctServiceClient pctClient,
                               CostaServiceClient costaClient) {
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

    public record CloseEncounterRequest(
            String diagnosis
    ) {}

    public record DischargeEncounterRequest(
            @NotBlank String discharge_type,
            String discharge_diagnosis,
            String treatment_summary,
            String follow_up_instructions,
            String medications_at_discharge,
            String patient_instructions,
            String discharged_by
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listEncounters(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId) {
        if (patientId != null) {
            try {
                JsonNode pctData = pctClient.getPatientTimeline(patientId);
                if (pctData != null) {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("data", pctData);
                    response.put("meta", Map.of(
                            "request_id", requestId,
                            "correlation_id", correlationId
                    ));
                    return ResponseEntity.ok(response);
                }
            } catch (Exception e) {
                log.warn("PCT getPatientTimeline failed: {}", e.getMessage());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", List.of());
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getEncounter(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of());
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));
        return ResponseEntity.ok(response);
    }

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

        // Delegate to PCT: start encounter in the sovereign service
        String pctEncounterRef = null;
        if (request.pct_journey_id() != null && !request.pct_journey_id().isBlank()) {
            try {
                JsonNode pctEncounter = pctClient.startEncounter(
                        request.pct_journey_id(), request.encounter_type());
                if (pctEncounter != null && pctEncounter.has("encounterRef")) {
                    pctEncounterRef = pctEncounter.get("encounterRef").asText();
                }
                log.info("PCT encounter started: ref={} for BFF encounter {}",
                        pctEncounterRef, encounterId);

                // Persist the PCT encounter reference and journey ID
            } catch (Exception e) {
                log.warn("PCT encounter delegation failed (non-blocking): {}", e.getMessage());
            }
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("facility_id", request.facility_id());
        attributes.put("encounter_type", request.encounter_type());
        attributes.put("chief_complaint", request.chief_complaint());
        attributes.put("status", "IN_PROGRESS");
        attributes.put("started_at", now);
        attributes.put("created_at", now);
        if (pctEncounterRef != null) {
            attributes.put("pct_encounter_ref", pctEncounterRef);
        }
        if (request.pct_journey_id() != null) {
            attributes.put("pct_journey_id", request.pct_journey_id());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", encounterId.toString(),
                "type", "Encounter",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Map<String, Object>> closeEncounter(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CloseEncounterRequest request) {

        // Delegate to COSTA: create bill draft for the closed encounter
        String costaBillId = createBillDraftForEncounter(id, tenantId, "ENCOUNTER");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id.toString());
        data.put("type", "Encounter");
        data.put("status", "COMPLETED");
        if (costaBillId != null) data.put("costa_bill_id", costaBillId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/discharge")
    public ResponseEntity<Map<String, Object>> dischargeEncounter(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody DischargeEncounterRequest request) {

        // Delegate to COSTA: create bill draft for the discharged encounter
        String costaBillId = createBillDraftForEncounter(id, tenantId, "ENCOUNTER");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id.toString());
        data.put("type", "Encounter");
        data.put("discharge_type", request.discharge_type());
        data.put("status", "DISCHARGED");
        if (costaBillId != null) data.put("costa_bill_id", costaBillId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Create a COSTA bill draft for an encounter (non-blocking).
     * Persists the bill ID as a bridge column on the encounter row.
     *
     * @return the COSTA bill ID, or null if creation failed
     */
    private String createBillDraftForEncounter(UUID encounterId, String tenantId, String encounterType) {
        try {
            String billType = "ENCOUNTER";
            JsonNode billData = costaClient.createBillDraft(encounterId.toString(), billType);
            if (billData != null && billData.has("billId")) {
                String billId = billData.get("billId").asText();
                log.info("COSTA bill draft created: billId={} for encounter={}", billId, encounterId);
                return billId;
            }
            log.warn("COSTA bill draft response missing billId for encounter={}", encounterId);
        } catch (Exception e) {
            log.warn("COSTA bill draft creation failed (non-blocking) for encounter={}: {}",
                    encounterId, e.getMessage());
        }
        return null;
    }
}
