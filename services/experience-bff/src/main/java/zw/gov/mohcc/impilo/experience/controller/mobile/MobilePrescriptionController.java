package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;

import java.util.*;

/**
 * Mobile prescription endpoints.
 * POST /internal/v1/mobile/provider/prescriptions              - create prescription
 * GET  /internal/v1/mobile/provider/prescriptions?patient_id=    - list (optional encounter_id filter)
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
            String medication_name,
            String medication,
            String generic_name,
            @NotBlank String dosage,
            String route,
            @NotBlank String frequency,
            String duration,
            Integer quantity,
            String instructions,
            String indication,
            String prescribed_by
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
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @Valid @RequestBody CreatePrescriptionRequest request) {
        try {
            String medicationName = firstNonBlank(request.medication_name(), request.medication());
            if (medicationName == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", Map.of("code", "MISSING_MEDICATION", "message", "medication_name is required"),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            String prescribedBy = firstNonBlank(request.prescribed_by(), actorId, "mobile-provider");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("patient_id", request.patient_id());
            payload.put("facility_id", request.facility_id());
            payload.put("encounter_id", request.encounter_id());
            payload.put("medication_name", medicationName);
            payload.put("generic_name", request.generic_name());
            payload.put("dosage", request.dosage());
            payload.put("route", request.route() != null ? request.route() : "PO");
            payload.put("frequency", request.frequency());
            payload.put("duration", request.duration());
            payload.put("quantity", request.quantity());
            payload.put("instructions", request.instructions());
            payload.put("indication", request.indication());
            payload.put("prescribed_by", prescribedBy);
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

    @GetMapping
    public ResponseEntity<Map<String, Object>> listPrescriptions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "encounter_id") String encounterId,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "MISSING_PATIENT_ID", "message", "patient_id query parameter is required"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        try {
            JsonNode data = pharmacyClient.getPatientPrescriptions(patientId, null, page, size);
            if (data != null) {
                JsonNode filtered = filterByEncounter(data, encounterId);
                return ResponseEntity.ok(Map.of(
                        "data", filtered,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return upstreamFailure("PHARMACY_UNAVAILABLE", "No prescription payload returned", requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("PHARMACY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
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
        try {
            Map<String, Object> payload = request != null && request.reason() != null
                    ? Map.of("reason", request.reason())
                    : Map.of();
            JsonNode data = pharmacyClient.cancelPrescription(id, payload);
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

    private static JsonNode filterByEncounter(JsonNode data, String encounterId) {
        if (encounterId == null || encounterId.isBlank() || !data.isArray()) {
            return data;
        }
        ArrayNode filtered = JsonNodeFactory.instance.arrayNode();
        for (JsonNode item : data) {
            JsonNode attrs = item.has("attributes") ? item.get("attributes") : item;
            String enc = attrs.path("encounter_id").asText("");
            if (encounterId.equals(enc)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Pharmacy upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
