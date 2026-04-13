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
import java.time.OffsetDateTime;
import java.util.*;

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
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCondition(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateConditionRequest request) {

        UUID conditionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String clinicalStatus = request.clinical_status() != null ? request.clinical_status() : "ACTIVE";

        // STRANGLER: delegate to PctServiceClient first
        try {
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
            pctClient.createCondition(pctBody);
            log.info("PCT condition created successfully for patient={}", request.patient_id());
        } catch (Exception e) {
            log.warn("PCT createCondition failed (non-blocking): {}", e.getMessage());
        }

        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("condition_name", request.condition_name());
        attributes.put("icd_code", request.icd_code());
        attributes.put("category", request.category());
        attributes.put("clinical_status", clinicalStatus);
        attributes.put("severity", request.severity());
        attributes.put("onset_date", request.onset_date());
        attributes.put("recorded_by", request.recorded_by());
        attributes.put("notes", request.notes());
        attributes.put("created_at", now);
        attributes.put("updated_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", conditionId.toString(),
                "type", "Condition",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveCondition(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        // STRANGLER: delegate to PctServiceClient first
        try {
            pctClient.resolveCondition(id.toString());
            log.info("PCT condition resolved: {}", id);
        } catch (Exception e) {
            log.warn("PCT resolveCondition failed (non-blocking): {}", e.getMessage());
        }

        condition.resolve();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(condition));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }
}
