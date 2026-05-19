package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mobile vitals — delegates recording to PCT vitals API using the same payload shape as {@link zw.gov.mohcc.impilo.experience.controller.VitalsController}.
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/vitals")
public class MobileVitalsController {

    private final PctServiceClient pctClient;

    public MobileVitalsController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
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

        Map<String, Object> pctBody = mobileVitalToPctBody(request);
        JsonNode created = pctClient.createVitals(pctBody);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", created != null ? created : Map.of(),
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

        List<JsonNode> saved = new ArrayList<>();
        for (RecordVitalRequest vital : request.vitals()) {
            JsonNode created = pctClient.createVitals(mobileVitalToPctBody(vital));
            if (created != null) {
                saved.add(created);
            }
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
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "encounter_id must be a numeric PCT encounter id");
            }
        }
        if (cpid == null || cpid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "either patient_id or encounter_id is required");
        }
        JsonNode data = pctClient.listVitals(cpid, page, size);
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> latestVitals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientId) {
        JsonNode data = pctClient.listVitals(patientId, 0, 1);
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : List.of(),
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
        pctClient.deleteVital(id);
        return ResponseEntity.ok(Map.of(
                "data", Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private static Map<String, Object> mobileVitalToPctBody(RecordVitalRequest request) {
        Map<String, Object> pctBody = new LinkedHashMap<>();
        pctBody.put("patient_id", request.patient_id());
        pctBody.put("encounter_id", request.encounter_id());
        String note = request.vital_type() + "=" + request.value()
                + (request.unit() != null && !request.unit().isBlank() ? " " + request.unit() : "");
        if (request.notes() != null && !request.notes().isBlank()) {
            note = note + " | " + request.notes();
        }
        pctBody.put("notes", note);
        applyTypedVitals(pctBody, request.vital_type(), request.value());
        return pctBody;
    }

    private static void applyTypedVitals(Map<String, Object> pctBody, String vitalType, BigDecimal value) {
        if (vitalType == null) {
            return;
        }
        switch (vitalType.trim().toUpperCase()) {
            case "SYSTOLIC" -> pctBody.put("systolic", value.intValue());
            case "DIASTOLIC" -> pctBody.put("diastolic", value.intValue());
            case "HEART_RATE", "PULSE" -> pctBody.put("heart_rate", value.intValue());
            case "TEMPERATURE" -> pctBody.put("temperature", value);
            case "RESPIRATORY_RATE", "RR" -> pctBody.put("respiratory_rate", value.intValue());
            case "SPO2", "OXYGEN_SATURATION" -> pctBody.put("oxygen_saturation", value);
            case "WEIGHT" -> pctBody.put("weight", value);
            case "HEIGHT" -> pctBody.put("height", value);
            case "PAIN_SCORE" -> pctBody.put("pain_score", value.intValue());
            default -> { /* value captured only in notes */ }
        }
    }
}
