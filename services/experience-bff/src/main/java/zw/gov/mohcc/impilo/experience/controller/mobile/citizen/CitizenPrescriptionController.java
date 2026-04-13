package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.util.*;

/**
 * Citizen prescription and refill endpoints.
 * GET  /internal/v1/mobile/citizen/prescriptions
 * GET  /internal/v1/mobile/citizen/prescriptions/{id}
 * POST /internal/v1/mobile/citizen/prescriptions/{id}/refill
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/prescriptions")
public class CitizenPrescriptionController {

    private final PharmacyServiceClient pharmacyClient;

    public CitizenPrescriptionController(PharmacyServiceClient pharmacyClient) {
        this.pharmacyClient = pharmacyClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 100);

        JsonNode prescriptions = pharmacyClient.getPatientPrescriptions(actorId, status, page, limit);

        // UUID patientId = resolvePatientId(tenantId, actorId);
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", prescriptions);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode prescription = pharmacyClient.getPrescription(id.toString());

        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(..., id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", prescription);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/refill")
    public ResponseEntity<Map<String, Object>> refill(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {

        JsonNode refillResult = pharmacyClient.requestRefill(id.toString(), body != null ? body : Map.of());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", refillResult);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
