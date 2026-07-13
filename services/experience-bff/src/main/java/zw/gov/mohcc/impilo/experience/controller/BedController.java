package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;

import java.util.*;

/**
 * Bed and ward management endpoints.
 *
 * GET  /internal/v1/beds/wards — list wards for a facility
 * GET  /internal/v1/beds — list beds with ward/status filter
 * POST /internal/v1/beds/{id}/status — change bed status
 * POST /internal/v1/beds/{id}/assign — assign patient to bed
 * POST /internal/v1/beds/{id}/discharge — discharge patient from bed
 *
 * <p>These endpoints propagate upstream outcomes honestly. The inpatient-service
 * bed-safety gate (gender bay, isolation, ICU capability, …) returns 4xx that the
 * UI MUST see — this controller previously caught every failure and returned a
 * fabricated 200 {status: OCCUPIED}, so an unsafe or failed placement rendered as
 * success. Client (4xx) errors now pass through with the upstream message; the
 * service being unreachable (5xx/connection) surfaces as 502.</p>
 */
@RestController
@RequestMapping("/internal/v1/beds")
public class BedController {

    private static final Logger log = LoggerFactory.getLogger(BedController.class);
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

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
            return ok(data != null ? data : ERROR_MAPPER.createArrayNode(), requestId, correlationId);
        } catch (Exception e) {
            return upstreamError("Inpatient listWards", e, requestId, correlationId);
        }
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
            return ok(data != null ? data : ERROR_MAPPER.createArrayNode(), requestId, correlationId);
        } catch (Exception e) {
            return upstreamError("Inpatient listBeds", e, requestId, correlationId);
        }
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateBedStatus(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, String> body) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>(body);
            JsonNode data = inpatientClient.updateBedStatus(id.toString(), payload);
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            return upstreamError("Inpatient updateBedStatus", e, requestId, correlationId);
        }
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Map<String, Object>> assignPatient(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, String> body) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>(body);
            JsonNode data = inpatientClient.assignPatientToBed(id.toString(), payload);
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            return upstreamError("Inpatient assignPatientToBed", e, requestId, correlationId);
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
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            return upstreamError("Inpatient dischargeBed", e, requestId, correlationId);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static ResponseEntity<Map<String, Object>> ok(JsonNode data, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", meta(requestId, correlationId));
        return ResponseEntity.ok(response);
    }

    private static ResponseEntity<Map<String, Object>> upstreamError(
            String operation, Exception e, String requestId, String correlationId) {
        // Client (4xx) errors — e.g. a bed-safety violation (409) or a bad assignment
        // (400) — are meaningful to the caller and must reach the UI with their status
        // and message, never be masked as a fabricated success.
        if (e instanceof HttpStatusCodeException ce && ce.getStatusCode().is4xxClientError()) {
            String message = extractUpstreamMessage(ce);
            log.warn("{} client error {}: {}", operation, ce.getStatusCode(), message);
            return ResponseEntity.status(ce.getStatusCode()).body(Map.of(
                    "error", Map.of("code", "BED_" + ce.getStatusCode().value(), "message", message),
                    "meta", meta(requestId, correlationId)));
        }
        log.warn("{} failed: {}", operation, e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", "INPATIENT_UNAVAILABLE", "message",
                        e.getMessage() != null ? e.getMessage() : "inpatient service unavailable"),
                "meta", meta(requestId, correlationId)));
    }

    private static Map<String, String> meta(String requestId, String correlationId) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("request_id", requestId);
        m.put("correlation_id", correlationId);
        return m;
    }

    /** Pull a human-readable message out of an upstream error body ({error:{message}} or {message}). */
    private static String extractUpstreamMessage(HttpStatusCodeException ce) {
        String body = ce.getResponseBodyAsString();
        if (body != null && !body.isBlank()) {
            try {
                JsonNode node = ERROR_MAPPER.readTree(body);
                JsonNode err = node.get("error");
                if (err != null && err.isObject() && err.hasNonNull("message")) {
                    return err.get("message").asText();
                }
                if (err != null && err.isTextual() && !err.asText().isBlank()) {
                    return err.asText();
                }
                if (node.hasNonNull("message") && !node.get("message").asText().isBlank()) {
                    return node.get("message").asText();
                }
            } catch (Exception ignored) {
                // fall through to status reason
            }
        }
        return ce.getStatusText();
    }
}
