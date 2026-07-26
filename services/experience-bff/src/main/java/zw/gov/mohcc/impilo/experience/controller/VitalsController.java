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
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vitals — proxies PCT vitals APIs.
 */
@RestController
@RequestMapping("/internal/v1/vitals")
public class VitalsController {

    private static final Logger log = LoggerFactory.getLogger(VitalsController.class);

    private final PctServiceClient pctClient;

    public VitalsController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    public record CreateVitalsRequest(
            @NotBlank String patient_id,
            String encounter_id,
            String recorded_by,
            Integer systolic,
            Integer diastolic,
            Integer heart_rate,
            BigDecimal temperature,
            Integer respiratory_rate,
            BigDecimal oxygen_saturation,
            BigDecimal weight,
            BigDecimal height,
            Integer pain_score,
            String notes
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listVitals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(required = false, name = "encounter_id") String encounterId) {

        String cpid = patientId;
        if ((cpid == null || cpid.isBlank()) && encounterId != null && !encounterId.isBlank()) {
            try {
                long enc = Long.parseLong(encounterId.trim());
                JsonNode encNode = pctClient.getEncounter(enc);
                if (encNode != null && encNode.has("subjectCpid")) {
                    cpid = encNode.get("subjectCpid").asText();
                }
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "encounter_id must be a numeric PCT encounter id when patient_id is omitted");
            } catch (Exception e) {
                // Falling through would blame the caller's encounter_id (400) for what is
                // actually an upstream outage, and the 400 handler cannot tell the difference.
                log.error("PCT getEncounter for vitals list failed for encounter={}: {}",
                        encounterId, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "error", "vitals_unavailable",
                        "message", "The encounter could not be resolved because PCT is "
                                   + "unreachable. Do not treat this as an absence of observations.",
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        }

        if (cpid == null || cpid.isBlank()) {
            // Vitals are per-patient — without a resolvable patient there is no list to return.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "patient_id or a resolvable encounter_id is required to list vitals");
        }

        try {
            JsonNode pctData = pctClient.listVitals(cpid, page, size);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            // An empty 200 here reads as "this patient has no recorded observations" — the same
            // reassuring shape a stable patient produces, said exactly when the system knows
            // least. Fail loudly rather than fabricate the absence.
            log.error("PCT listVitals failed for patient={}: {}", cpid, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "vitals_unavailable",
                    "message", "Vital signs could not be retrieved. Do not treat this as an "
                               + "absence of observations.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createVitals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateVitalsRequest request) {

        Map<String, Object> pctBody = new LinkedHashMap<>();
        pctBody.put("patient_id", request.patient_id());
        pctBody.put("encounter_id", request.encounter_id());
        pctBody.put("recorded_by", request.recorded_by());
        pctBody.put("systolic", request.systolic());
        pctBody.put("diastolic", request.diastolic());
        pctBody.put("heart_rate", request.heart_rate());
        pctBody.put("temperature", request.temperature());
        pctBody.put("respiratory_rate", request.respiratory_rate());
        pctBody.put("oxygen_saturation", request.oxygen_saturation());
        pctBody.put("weight", request.weight());
        pctBody.put("height", request.height());
        pctBody.put("pain_score", request.pain_score());
        pctBody.put("notes", request.notes());

        JsonNode created = pctClient.createVitals(pctBody);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", created != null ? created : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
