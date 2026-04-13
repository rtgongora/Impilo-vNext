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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Vitals management endpoints.
 * GET  /internal/v1/vitals?patient_id= — list vitals for patient (paged).
 * GET  /internal/v1/vitals?encounter_id= — list vitals for encounter.
 * POST /internal/v1/vitals — record new vitals entry.
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
        throw new UnsupportedOperationException("Endpoint pending migration to sovereign service");
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createVitals(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateVitalsRequest request) {

        UUID vitalsId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        // Calculate BMI if weight and height are provided
        BigDecimal bmi = null;
        if (request.weight() != null && request.height() != null
                && request.height().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal heightM = request.height().divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
            bmi = request.weight().divide(heightM.multiply(heightM), 2, java.math.RoundingMode.HALF_UP);
        }

        // STRANGLER: delegate to PctServiceClient first
        try {
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
            pctClient.createVitals(pctBody);
            log.info("PCT vitals created successfully for patient={}", request.patient_id());
        } catch (Exception e) {
            log.warn("PCT createVitals failed (non-blocking): {}", e.getMessage());
        }

        // STRANGLER: migrated to PctServiceClient — dual-write to local BFF table as backup cache

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("recorded_by", request.recorded_by());
        attributes.put("systolic", request.systolic());
        attributes.put("diastolic", request.diastolic());
        attributes.put("heart_rate", request.heart_rate());
        attributes.put("temperature", request.temperature());
        attributes.put("respiratory_rate", request.respiratory_rate());
        attributes.put("oxygen_saturation", request.oxygen_saturation());
        attributes.put("weight", request.weight());
        attributes.put("height", request.height());
        attributes.put("bmi", bmi);
        attributes.put("pain_score", request.pain_score());
        attributes.put("notes", request.notes());
        attributes.put("recorded_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", vitalsId.toString(),
                "type", "VitalsRecord",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
