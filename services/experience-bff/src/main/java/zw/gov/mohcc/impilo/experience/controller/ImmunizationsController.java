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
 * Immunization management endpoints.
 * GET  /internal/v1/immunizations?patient_id= — list immunizations for patient (paged).
 * POST /internal/v1/immunizations — record immunization.
 */
@RestController
@RequestMapping("/internal/v1/immunizations")
public class ImmunizationsController {

    private static final Logger log = LoggerFactory.getLogger(ImmunizationsController.class);

    private final PctServiceClient pctClient;

    public ImmunizationsController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    public record CreateImmunizationRequest(
            @NotBlank String patient_id,
            String encounter_id,
            @NotBlank String vaccine_name,
            String vaccine_code,
            Integer dose_number,
            String dose_sequence,
            String lot_number,
            String site,
            String route,
            String administered_by,
            LocalDate expiration_date,
            String notes
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listImmunizations(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId) {
        if (patientId != null) {
            try {
                JsonNode pctData = pctClient.listImmunizations(patientId, page, size);
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
                log.warn("PCT listImmunizations failed: {}", e.getMessage());
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

    @PostMapping
    public ResponseEntity<Map<String, Object>> createImmunization(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateImmunizationRequest request) {

        UUID immunizationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        try {
            Map<String, Object> pctBody = new LinkedHashMap<>();
            pctBody.put("patient_id", request.patient_id());
            pctBody.put("encounter_id", request.encounter_id());
            pctBody.put("vaccine_name", request.vaccine_name());
            pctBody.put("vaccine_code", request.vaccine_code());
            pctBody.put("dose_number", request.dose_number());
            pctBody.put("dose_sequence", request.dose_sequence());
            pctBody.put("lot_number", request.lot_number());
            pctBody.put("site", request.site());
            pctBody.put("route", request.route());
            pctBody.put("administered_by", request.administered_by());
            pctBody.put("expiration_date", request.expiration_date());
            pctBody.put("notes", request.notes());
            pctClient.createImmunization(pctBody);
            log.info("PCT immunization created successfully for patient={}", request.patient_id());
        } catch (Exception e) {
            log.warn("PCT createImmunization failed (non-blocking): {}", e.getMessage());
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("vaccine_name", request.vaccine_name());
        attributes.put("vaccine_code", request.vaccine_code());
        attributes.put("dose_number", request.dose_number());
        attributes.put("dose_sequence", request.dose_sequence());
        attributes.put("lot_number", request.lot_number());
        attributes.put("site", request.site());
        attributes.put("route", request.route());
        attributes.put("status", "COMPLETED");
        attributes.put("administered_by", request.administered_by());
        attributes.put("administered_at", now);
        attributes.put("expiration_date", request.expiration_date());
        attributes.put("notes", request.notes());
        attributes.put("created_at", now);
        attributes.put("updated_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", immunizationId.toString(),
                "type", "Immunization",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
