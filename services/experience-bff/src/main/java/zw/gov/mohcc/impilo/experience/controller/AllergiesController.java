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
 * Allergy management endpoints.
 * GET    /internal/v1/allergies?patient_id= — list allergies for patient.
 * POST   /internal/v1/allergies — create allergy.
 * DELETE /internal/v1/allergies/{id} — mark allergy INACTIVE.
 */
@RestController
@RequestMapping("/internal/v1/allergies")
public class AllergiesController {

    private static final Logger log = LoggerFactory.getLogger(AllergiesController.class);

    private final PctServiceClient pctClient;

    public AllergiesController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    public record CreateAllergyRequest(
            @NotBlank String patient_id,
            @NotBlank String allergen,
            String allergen_type,
            String reaction,
            String severity,
            LocalDate onset_date,
            String recorded_by
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listAllergies(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "patient_id") String patientId) {

        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        try {
            JsonNode pctData = pctClient.listAllergies(patientId);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT listAllergies failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAllergy(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateAllergyRequest request) {
        Map<String, Object> pctBody = new LinkedHashMap<>();
        pctBody.put("patient_id", request.patient_id());
        pctBody.put("allergen", request.allergen());
        pctBody.put("allergen_type", request.allergen_type());
        pctBody.put("reaction", request.reaction());
        pctBody.put("severity", request.severity());
        pctBody.put("onset_date", request.onset_date());
        pctBody.put("recorded_by", request.recorded_by());

        JsonNode result = pctClient.createAllergy(pctBody);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", result != null ? result : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deactivateAllergy(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        JsonNode result = pctClient.deactivateAllergy(id.toString());
        return ResponseEntity.ok(Map.of(
                "data", result != null ? result : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
