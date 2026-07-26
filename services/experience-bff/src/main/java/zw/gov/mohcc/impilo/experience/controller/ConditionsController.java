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
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Condition management endpoints.
 * GET  /internal/v1/conditions?patient_id= — list conditions for patient (paged).
 * POST /internal/v1/conditions — create condition.
 * POST /internal/v1/conditions/{id}/resolve — resolve condition.
 */
@RestController
@RequestMapping("/internal/v1/conditions")
public class ConditionsController {

    private static final Logger log = LoggerFactory.getLogger(ConditionsController.class);

    private final PctServiceClient pctClient;

    public ConditionsController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    public record CreateConditionRequest(
            @NotBlank String patient_id,
            String encounter_id,
            @NotBlank String condition_name,
            String icd_code,
            String category,
            String clinical_status,
            String severity,
            LocalDate onset_date,
            String recorded_by,
            String notes
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listConditions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId) {
        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        try {
            JsonNode pctData = pctClient.listConditions(patientId, page, size);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            // An empty 200 here reads as "this patient has no diagnosed conditions", which is an
            // affirmative clinical finding and would be a fabricated one. Fail loudly instead.
            log.error("PCT listConditions failed for patient={}: {}", patientId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "condition_list_unavailable",
                    "message", "Conditions could not be retrieved. Do not treat this as an "
                               + "absence of conditions.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCondition(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateConditionRequest request) {

        String clinicalStatus = request.clinical_status() != null ? request.clinical_status() : "ACTIVE";

        Map<String, Object> pctBody = new LinkedHashMap<>();
        pctBody.put("patient_id", request.patient_id());
        pctBody.put("encounter_id", request.encounter_id());
        pctBody.put("condition_name", request.condition_name());
        pctBody.put("icd_code", request.icd_code());
        pctBody.put("category", request.category());
        pctBody.put("clinical_status", clinicalStatus);
        pctBody.put("severity", request.severity());
        pctBody.put("onset_date", request.onset_date());
        pctBody.put("recorded_by", request.recorded_by());
        pctBody.put("notes", request.notes());

        JsonNode created = pctClient.createCondition(pctBody);
        log.info("PCT condition created for patient={}", request.patient_id());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", created != null ? created : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveCondition(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        JsonNode result = pctClient.resolveCondition(id.toString());
        log.info("PCT condition resolved: {}", id);

        return ResponseEntity.ok(Map.of(
                "data", result != null ? result : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
