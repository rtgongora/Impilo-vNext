package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;

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
        if (patientId != null && !patientId.isBlank()) {
            try {
                JsonNode data = pharmacyClient.getPatientPrescriptions(patientId, status, page, size);
                if (data != null) {
                    return ResponseEntity.ok(Map.of(
                            "data", data,
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                }
                return upstreamFailure("PHARMACY_UNAVAILABLE", "No prescription payload returned", requestId, correlationId);
            } catch (Exception e) {
                return upstreamFailure("PHARMACY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", Map.of("code", "MISSING_PATIENT_ID", "message", "patient_id query parameter is required"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/prescriptions")
    public ResponseEntity<Map<String, Object>> createPrescription(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePrescriptionRequest request) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("patient_id", request.patient_id());
            payload.put("facility_id", request.facility_id());
            payload.put("encounter_id", request.encounter_id());
            payload.put("medication_name", request.medication_name());
            payload.put("generic_name", request.generic_name());
            payload.put("dosage", request.dosage());
            payload.put("route", request.route());
            payload.put("frequency", request.frequency());
            payload.put("duration", request.duration());
            payload.put("quantity", request.quantity());
            payload.put("instructions", request.instructions());
            payload.put("indication", request.indication());
            payload.put("prescribed_by", request.prescribed_by());
            JsonNode data = pharmacyClient.createPrescription(payload);
            if (data != null) {
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return upstreamFailure("PHARMACY_UNAVAILABLE", "No prescription payload returned", requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("PHARMACY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/dispense")
    public ResponseEntity<Map<String, Object>> dispense(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody DispenseRequest request) {
        try {
            UUID prescriptionId = UUID.fromString(request.prescription_id());
            JsonNode data = pharmacyClient.dispensePrescription(
                    prescriptionId,
                    Map.of("dispensed_by", request.dispensed_by()));
            if (data != null) {
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return upstreamFailure("PHARMACY_UNAVAILABLE", "No dispense payload returned", requestId, correlationId);
        } catch (IllegalArgumentException badId) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "INVALID_PRESCRIPTION_ID", "message", "prescription_id must be a UUID"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("PHARMACY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
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
        try {
            JsonNode data = pharmacyClient.cancelPrescription(id, Map.of());
            if (data != null) {
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return upstreamFailure("PHARMACY_UNAVAILABLE", "No cancel payload returned", requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("PHARMACY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    // ── Sovereign pharmacy-service (dispense orders / worklists) ─────────────

    /**
     * Facility dispense-order worklist (cross-service journey read-path).
     * GET /internal/v1/pharmacy/orders?status=&page=&size=
     *
     * <p>Resolves the facility from the {@code X-Facility-ID} trust header and returns the
     * pharmacy-service worklist. When no facility context is supplied the endpoint returns an
     * empty list with 200 so the worklist surface can still render rather than erroring.</p>
     */
    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> listOrders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (facilityId == null || facilityId.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                            "note", "no facility context; supply X-Facility-ID for the worklist")));
        }
        try {
            JsonNode data = pharmacyClient.getWorklist(facilityId, status);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId, "facility_id", facilityId)));
        } catch (Exception e) {
            return upstreamFailure("PHARMACY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/upstream/dispense-orders/patient/{cpid}")
    public ResponseEntity<Map<String, Object>> listUpstreamDispenseOrdersForPatient(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String cpid) {
        try {
            JsonNode data = pharmacyClient.getPatientDispenseOrders(cpid);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("PHARMACY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
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
                    "data", data != null ? data : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("PHARMACY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
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
            return upstreamFailure("PHARMACY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Pharmacy upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    /**
     * Claim a medication pickup with a token, optionally verifying the collector's biometric
     * (A4 seam). Body: {@code {token, deviceFingerprint?, biometricSubjectRef?, biometricModality?,
     * biometricProbeBase64?}}. A NO_MATCH biometric is a real service 4xx and is surfaced honestly
     * (COLLECTION_REJECTED), not masked as a 502.
     */
    @PostMapping("/pickup/claim")
    public ResponseEntity<Map<String, Object>> claimPickup(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        if (body == null || body.get("token") == null || body.get("token").toString().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "TOKEN_REQUIRED", "message", "A pickup token is required"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        try {
            JsonNode data = pharmacyClient.claimPickup(body);
            if (data == null) {
                return upstreamFailure("PHARMACY_UNAVAILABLE", "No claim payload returned", requestId, correlationId);
            }
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // Honest passthrough: invalid/expired token or a biometric NO_MATCH is a real rejection.
            return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                    "error", Map.of("code", "COLLECTION_REJECTED",
                            "message", "Collection was rejected — invalid/expired token or biometric mismatch."),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return upstreamFailure("PHARMACY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }
}
