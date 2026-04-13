package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bed and ward management endpoints.
 *
 * GET  /internal/v1/beds/wards — list wards for a facility
 * GET  /internal/v1/beds — list beds with ward/status filter
 * POST /internal/v1/beds/{id}/status — change bed status
 * POST /internal/v1/beds/{id}/assign — assign patient to bed
 * POST /internal/v1/beds/{id}/discharge — discharge patient from bed
 */
@RestController
@RequestMapping("/internal/v1/beds")
public class BedController {

    private static final Logger log = LoggerFactory.getLogger(BedController.class);

    private final InpatientServiceClient inpatientClient;

    public BedController(InpatientServiceClient inpatientClient) {
        this.inpatientClient = inpatientClient;
    }

    @GetMapping("/wards")
    public ResponseEntity<Map<String, Object>> listWards(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") UUID facilityId) {
        try {
            JsonNode data = inpatientClient.listWards(facilityId.toString());
            if (data != null) {
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        } catch (Exception e) {
            log.warn("Inpatient listWards failed: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listBeds(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam(required = false, name = "ward_id") UUID wardId,
            @RequestParam(required = false) String status) {
        try {
            JsonNode data = inpatientClient.listBeds(
                    facilityId.toString(),
                    wardId != null ? wardId.toString() : null,
                    status);
            if (data != null) {
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        } catch (Exception e) {
            log.warn("Inpatient listBeds failed: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateBedStatus(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        try {
            JsonNode data = inpatientClient.updateBedStatus(id.toString(), body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Inpatient updateBedStatus failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Map<String, Object>> assignPatient(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        try {
            JsonNode data = inpatientClient.assignPatientToBed(id.toString(), body);
            return ResponseEntity.status(HttpStatus.OK).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Inpatient assignPatientToBed failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.OK).body(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/{id}/discharge")
    public ResponseEntity<Map<String, Object>> dischargeBed(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = inpatientClient.dischargeBed(id.toString());
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Inpatient dischargeBed failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
