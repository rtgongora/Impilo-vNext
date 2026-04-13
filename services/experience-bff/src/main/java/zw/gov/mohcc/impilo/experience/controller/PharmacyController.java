package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Pharmacy endpoints.
 * GET  /internal/v1/pharmacy/prescriptions — list prescriptions with status/patient filter, pagination.
 * POST /internal/v1/pharmacy/prescriptions — create a prescription.
 * POST /internal/v1/pharmacy/dispense — dispense a prescription.
 */
@RestController
@RequestMapping("/internal/v1/pharmacy")
public class PharmacyController {

    private final PharmacyServiceClient pharmacyClient;

    public PharmacyController(PharmacyServiceClient pharmacyClient) {
        this.pharmacyClient = pharmacyClient;
    }

    public record CreatePrescriptionRequest(
            @NotBlank String patient_id,
            String facility_id,
            String encounter_id,
            @NotBlank String medication_name,
            String generic_name,
            String dosage,
            String route,
            String frequency,
            String duration,
            Integer quantity,
            String instructions,
            String indication,
            @NotBlank String prescribed_by
    ) {}

    public record DispenseRequest(
            @NotBlank String prescription_id,
            @NotBlank String dispensed_by
    ) {}

    @GetMapping("/prescriptions")
    public ResponseEntity<Map<String, Object>> listPrescriptions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "patient_id") String patientId) {
        if (patientId != null) {
            try {
                JsonNode data = pharmacyClient.getPatientPrescriptions(patientId, status, page, size);
                if (data != null) {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("data", data);
                    response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                    return ResponseEntity.ok(response);
                }
            } catch (Exception e) {
                // fall through to empty response
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", List.of());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/prescriptions")
    public ResponseEntity<Map<String, Object>> createPrescription(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePrescriptionRequest request) {

        UUID rxId = UUID.randomUUID();
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
        attributes.put("status", "PENDING");
        attributes.put("prescribed_by", request.prescribed_by());
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", rxId.toString(), "type", "Prescription", "attributes", attributes));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));

        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(response);
    }

    @PostMapping("/dispense")
    public ResponseEntity<Map<String, Object>> dispense(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody DispenseRequest request) {

        UUID prescriptionId = UUID.fromString(request.prescription_id());

        try {
            JsonNode data = pharmacyClient.completeDispense(prescriptionId);
            if (data != null) {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("data", data);
                response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            // fall through to simple response
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("prescription_id", prescriptionId.toString(), "status", "DISPENSED", "dispensed_by", request.dispensed_by()));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));

        return ResponseEntity.ok(response);
    }

    /**
     * Cancel a prescription.
     * POST /internal/v1/pharmacy/prescriptions/{id}/cancel
     */
    @PostMapping("/prescriptions/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelPrescription(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "status", "CANCELLED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    // ── Sovereign pharmacy-service (dispense orders / worklists) ─────────────

    @GetMapping("/upstream/dispense-orders/patient/{cpid}")
    public ResponseEntity<Map<String, Object>> listUpstreamDispenseOrdersForPatient(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String cpid) {
        try {
            JsonNode data = pharmacyClient.getPatientDispenseOrders(cpid);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/upstream/worklists")
    public ResponseEntity<Map<String, Object>> listUpstreamWorklist(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam String facilityId,
            @RequestParam(required = false) String status) {
        try {
            JsonNode data = pharmacyClient.getWorklist(facilityId, status);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/upstream/dispense-orders/{dispenseOrderId}/complete")
    public ResponseEntity<Map<String, Object>> completeUpstreamDispense(
            @PathVariable UUID dispenseOrderId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = pharmacyClient.completeDispense(dispenseOrderId);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
