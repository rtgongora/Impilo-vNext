package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.clinical.VitalsObservationBridge;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mobile vitals — per-reading rows ({@code vital_type} + value) over pct's discrete observations,
 * which they map onto one-to-one. {@link VitalsObservationBridge} owns the translation; a batch
 * posts through pct's transactional batch endpoint so a partially recorded round can't happen.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/vitals")
public class MobileVitalsController {

    private final PctServiceClient pctClient;
    private final VitalsObservationBridge bridge;

    public MobileVitalsController(PctServiceClient pctClient, VitalsObservationBridge bridge) {
        this.pctClient = pctClient;
        this.bridge = bridge;
    }

    public record RecordVitalRequest(
            @NotBlank String encounter_id,
            @NotBlank String patient_id,
            @NotBlank String vital_type,
            @NotNull BigDecimal value,
            String unit,
            String notes
    ) {}

    public record BatchVitalsRequest(
            @NotNull List<RecordVitalRequest> vitals
    ) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> recordVital(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RecordVitalRequest request) {

        JsonNode created = pctClient.recordObservation(bridge.toSingleObservationBody(
                request.patient_id(), request.encounter_id(), request.vital_type(),
                request.value(), request.unit(), request.notes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", created != null ? bridge.toMobileRow(created) : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> recordVitalsBatch(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody BatchVitalsRequest request) {

        List<Map<String, Object>> bodies = new ArrayList<>(request.vitals().size());
        for (RecordVitalRequest vital : request.vitals()) {
            bodies.add(bridge.toSingleObservationBody(
                    vital.patient_id(), vital.encounter_id(), vital.vital_type(),
                    vital.value(), vital.unit(), vital.notes()));
        }
        if (bodies.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vitals must be a non-empty list");
        }
        // One transaction in pct: the old per-item loop could record half a round and stop,
        // leaving missing readings indistinguishable from questions never asked.
        JsonNode created = pctClient.recordObservationsBatch(bodies);
        List<Map<String, Object>> saved = new ArrayList<>();
        if (created != null && created.isArray()) {
            created.forEach(obs -> saved.add(bridge.toMobileRow(obs)));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", saved,
                "meta", Map.of(
                        "request_id", requestId,
                        "correlation_id", correlationId,
                        "count", saved.size())));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listVitals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "encounter_id", required = false) String encounterId,
            @RequestParam(name = "patient_id", required = false) String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        String cpid = null;
        String encounterFilter = null;
        if (patientId != null && !patientId.isBlank()) {
            cpid = patientId.trim();
        } else if (encounterId != null && !encounterId.isBlank()) {
            try {
                long enc = Long.parseLong(encounterId.trim());
                JsonNode encNode = pctClient.getEncounter(enc);
                if (encNode == null || !encNode.has("subjectCpid")) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Encounter not found");
                }
                cpid = encNode.get("subjectCpid").asText();
                encounterFilter = encounterId.trim();
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "encounter_id must be a numeric PCT encounter id");
            }
        }
        if (cpid == null || cpid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "either patient_id or encounter_id is required");
        }
        JsonNode observations = pctClient.listObservationsForSubject(cpid, encounterFilter);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (observations != null && observations.isArray()) {
            for (JsonNode obs : observations) {
                if (bridge.isLiveVitalSign(obs)) {
                    rows.add(bridge.toMobileRow(obs));
                }
            }
        }
        int from = Math.min(Math.max(page, 0) * Math.max(size, 1), rows.size());
        int to = Math.min(from + Math.max(size, 1), rows.size());
        return ResponseEntity.ok(Map.of(
                "data", rows.subList(from, to),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    /** Latest reading per vital type — the monitor view's contract. */
    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> latestVitals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientId) {
        JsonNode observations = pctClient.listObservationsForSubject(patientId, null);
        // pct returns newest first; keep the first row seen per vital type.
        Map<String, Map<String, Object>> latestByType = new LinkedHashMap<>();
        if (observations != null && observations.isArray()) {
            for (JsonNode obs : observations) {
                if (!bridge.isLiveVitalSign(obs)) {
                    continue;
                }
                Map<String, Object> row = bridge.toMobileRow(obs);
                latestByType.putIfAbsent((String) row.get("vital_type"), row);
            }
        }
        return ResponseEntity.ok(Map.of(
                "data", new ArrayList<>(latestByType.values()),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteVital(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        try {
            // The record-honest form of deletion: pct voids the observation (ENTERED_IN_ERROR)
            // rather than erasing a clinical fact that downstream engines may already have read.
            pctClient.voidObservation(id, "withdrawn from mobile provider app");
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), e.getResponseBodyAsString());
        }
        return ResponseEntity.ok(Map.of(
                "data", Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
