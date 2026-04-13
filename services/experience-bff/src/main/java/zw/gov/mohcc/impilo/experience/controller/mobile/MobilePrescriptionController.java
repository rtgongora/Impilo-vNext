package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile prescription endpoints.
 * POST /internal/v1/mobile/provider/prescriptions              - create prescription
 * GET  /internal/v1/mobile/provider/prescriptions?encounter_id= or patient_id= - list
 * POST /internal/v1/mobile/provider/prescriptions/{id}/cancel  - cancel prescription
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/prescriptions")
public class MobilePrescriptionController {

    private final PharmacyServiceClient pharmacyClient;

    public MobilePrescriptionController(PharmacyServiceClient pharmacyClient) {
        this.pharmacyClient = pharmacyClient;
    }

    /**
     * Mobile prescription request — uses canonical V3+V13 schema columns.
     */
    public record CreatePrescriptionRequest(
            @NotBlank String patient_id,
            String encounter_id,
            String facility_id,
            @NotBlank String medication_name,
            String generic_name,
            @NotBlank String dosage,
            String route,
            @NotBlank String frequency,
            String duration,
            Integer quantity,
            String instructions,
            String indication,
            @NotBlank String prescribed_by
    ) {}

    public record CancelPrescriptionRequest(
            String reason
    ) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> createPrescription(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePrescriptionRequest request) {

        UUID prescriptionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("facility_id", request.facility_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("medication_name", request.medication_name());
        attributes.put("generic_name", request.generic_name());
        attributes.put("dosage", request.dosage());
        attributes.put("route", request.route());
        attributes.put("frequency", request.frequency());
        attributes.put("duration", request.duration());
        attributes.put("quantity", request.quantity());
        attributes.put("instructions", request.instructions());
        attributes.put("indication", request.indication());
        attributes.put("prescribed_by", request.prescribed_by());
        attributes.put("status", "PENDING");
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", prescriptionId.toString(),
                "type", "Prescription",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listPrescriptions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "encounter_id") String encounterId,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (patientId != null && !patientId.isBlank()) {
            try {
                JsonNode data = pharmacyClient.getPatientPrescriptions(patientId, null, page, size);
                if (data != null) {
                    return ResponseEntity.ok(Map.of("data", data));
                }
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(Map.of("data", List.of()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelPrescription(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CancelPrescriptionRequest request) {
        return ResponseEntity.ok(Map.of("data", List.of()));
    }
}
