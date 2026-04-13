package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encounter management — proxies PCT encounter lifecycle; optional COSTA billing side-effects.
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
            @NotBlank String pct_journey_id,
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
        if (patientId == null || patientId.isBlank()) {
            // TODO: wire to PctServiceClient when PCT exposes facility/workspace encounter listing
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        try {
            JsonNode pctData = pctClient.getPatientTimeline(patientId);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT getPatientTimeline failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getEncounter(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        long encId = parsePctEncounterId(id);
        try {
            JsonNode data = pctClient.getEncounter(encId);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT getEncounter failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Encounter not found");
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createEncounter(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateEncounterRequest request) {

        JsonNode pctEncounter = pctClient.startEncounter(
                request.pct_journey_id(), request.encounter_type());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", pctEncounter != null ? pctEncounter : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Map<String, Object>> closeEncounter(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CloseEncounterRequest request) {

        long encId = parsePctEncounterId(id);
        JsonNode pct = pctClient.completeEncounter(encId);
        String costaBillId = createBillDraftForEncounter(String.valueOf(encId), tenantId, "ENCOUNTER");

        Map<String, Object> meta = new LinkedHashMap<>(Map.of(
                "request_id", requestId,
                "correlation_id", correlationId));
        if (costaBillId != null) {
            meta.put("costa_bill_id", costaBillId);
        }
        return ResponseEntity.ok(Map.of(
                "data", pct != null ? pct : Map.of(),
                "meta", meta));
    }

    @PostMapping("/{id}/discharge")
    public ResponseEntity<Map<String, Object>> dischargeEncounter(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody DischargeEncounterRequest request) {

        long encId = parsePctEncounterId(id);
        JsonNode enc = pctClient.getEncounter(encId);
        if (enc == null || !enc.has("journeyId")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PCT encounter missing journeyId");
        }
        String journeyId = enc.get("journeyId").asText();
        JsonNode discharge = pctClient.startDischarge(journeyId, request.discharge_type());

        String costaBillId = createBillDraftForEncounter(String.valueOf(encId), tenantId, "ENCOUNTER");
        Map<String, Object> meta = new LinkedHashMap<>(Map.of(
                "request_id", requestId,
                "correlation_id", correlationId));
        if (costaBillId != null) {
            meta.put("costa_bill_id", costaBillId);
        }
        return ResponseEntity.ok(Map.of(
                "data", discharge != null ? discharge : Map.of(),
                "meta", meta));
    }

    private String createBillDraftForEncounter(String encounterKey, String tenantId, String encounterType) {
        try {
            JsonNode billData = costaClient.createBillDraft(encounterKey, encounterType);
            if (billData != null && billData.has("billId")) {
                String billId = billData.get("billId").asText();
                log.info("COSTA bill draft created: billId={} for encounter={}", billId, encounterKey);
                return billId;
            }
            log.warn("COSTA bill draft response missing billId for encounter={}", encounterKey);
        } catch (Exception e) {
            log.warn("COSTA bill draft creation failed (non-blocking) for encounter={}: {}",
                    encounterKey, e.getMessage());
        }
        return null;
    }

    private static long parsePctEncounterId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Encounter id must be the numeric PCT encounter id");
        }
    }
}
