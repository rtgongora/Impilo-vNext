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
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Lab order management endpoints.
 * GET  /internal/v1/lab-orders?patient_id= — list orders for patient (paged).
 * GET  /internal/v1/lab-orders?status= — list orders by status (paged).
 * GET  /internal/v1/lab-orders/{id} — get single order.
 * POST /internal/v1/lab-orders — create lab order.
 * POST /internal/v1/lab-orders/{id}/collect — mark as collected.
 * POST /internal/v1/lab-orders/{id}/result — mark as resulted.
 */
@RestController
@RequestMapping("/internal/v1/lab-orders")
public class LabOrdersController {

    private static final Logger log = LoggerFactory.getLogger(LabOrdersController.class);

    private final OrosServiceClient orosClient;

    public LabOrdersController(OrosServiceClient orosClient) {
        this.orosClient = orosClient;
    }

    public record CreateLabOrderRequest(
            @NotBlank String patient_id,
            String encounter_id,
            @NotBlank String test_name,
            String test_code,
            String category,
            String priority,
            String clinical_notes,
            @NotBlank String ordered_by,
            String ordered_by_name,
            String facility_id,
            String patient_cpid,
            String pct_encounter_ref
    ) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> listLabOrders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(required = false, name = "status") String status) {
        if (patientId != null) {
            try {
                JsonNode orosData = orosClient.getPatientOrders(patientId);
                if (orosData != null) {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("data", orosData);
                    response.put("meta", Map.of(
                            "request_id", requestId,
                            "correlation_id", correlationId
                    ));
                    return ResponseEntity.ok(response);
                }
            } catch (Exception e) {
                log.warn("OROS getPatientOrders failed: {}", e.getMessage());
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

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getLabOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode orderData = orosClient.getOrder(id.toString());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", orderData != null ? orderData : Map.of());
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createLabOrder(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateLabOrderRequest request) {

        UUID orderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String orosType = resolveOrosOrderType(request.category());
        String orderNumber = orosType + "-" + now.toInstant().toEpochMilli();

        // Delegate to OROS: place the order in the sovereign order orchestration service
        String orosOrderId = null;
        if (request.patient_cpid() != null && !request.patient_cpid().isBlank()) {
            try {
                List<Map<String, Object>> items = List.of(Map.of(
                        "code", request.test_code() != null ? request.test_code() : request.test_name(),
                        "displayName", request.test_name(),
                        "quantity", 1
                ));
                JsonNode orosData = orosClient.placeOrder(
                        orosType,
                        request.priority() != null ? request.priority() : "ROUTINE",
                        request.patient_cpid(),
                        request.pct_encounter_ref(),
                        request.clinical_notes(),
                        items);
                if (orosData != null && orosData.has("orderId")) {
                    orosOrderId = orosData.get("orderId").asText();
                }
                log.info("OROS order placed: {} for BFF lab order {}", orosOrderId, orderId);
            } catch (Exception e) {
                log.warn("OROS order delegation failed (non-blocking): {}", e.getMessage());
            }
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("patient_id", request.patient_id());
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("order_number", orderNumber);
        attributes.put("test_name", request.test_name());
        attributes.put("test_code", request.test_code());
        attributes.put("category", request.category());
        attributes.put("priority", request.priority() != null ? request.priority() : "ROUTINE");
        attributes.put("status", "ORDERED");
        attributes.put("clinical_notes", request.clinical_notes());
        attributes.put("ordered_by", request.ordered_by());
        attributes.put("ordered_by_name", request.ordered_by_name());
        attributes.put("facility_id", request.facility_id());
        attributes.put("created_at", now);
        attributes.put("updated_at", now);
        if (orosOrderId != null) {
            attributes.put("oros_order_id", orosOrderId);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", orderId.toString(),
                "type", "LabOrder",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/collect")
    public ResponseEntity<Map<String, Object>> collectLabOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "status", "COLLECTED"));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/result")
    public ResponseEntity<Map<String, Object>> resultLabOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        if (body != null) {
            try {
                boolean hasCritical = false;
                if (body.containsKey("result_data") && body.get("result_data") instanceof List<?> rdList) {
                    hasCritical = rdList.stream().anyMatch(item ->
                            item instanceof Map<?, ?> m && "CRITICAL".equals(m.get("interpretation")));
                }
                orosClient.postResult(id.toString(), "LAB", body.get("result_data"), hasCritical);
                log.info("OROS result posted for order={}", id);
            } catch (Exception e) {
                log.warn("OROS result delegation failed (non-blocking): {}", e.getMessage());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "status", "RESULTED"));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Acknowledge a lab order result (clinician review).
     * POST /internal/v1/lab-orders/{id}/acknowledge
     *
     * Transitions the order to REVIEWED status and delegates to OROS.
     */
    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeLabOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        String notes = body != null && body.containsKey("notes") ? (String) body.get("notes") : null;

        try {
            orosClient.acknowledgeOrder(id.toString(), "CLINICIAN", notes);
            log.info("OROS acknowledgement posted for order={}", id);
        } catch (Exception e) {
            log.warn("OROS acknowledgement failed (non-blocking): {}", e.getMessage());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "status", "REVIEWED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelLabOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        String reason = body != null && body.containsKey("reason") ? (String) body.get("reason") : null;

        try {
            orosClient.cancelOrder(id.toString(),
                    reason != null ? reason : "Cancelled from experience UI");
            log.info("OROS order cancelled for lab order={}", id);
        } catch (Exception e) {
            log.warn("OROS cancel failed (non-blocking): {}", e.getMessage());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "status", "CANCELLED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /** Maps UI category to OROS order type (LAB, IMAGING, PHARMACY, PROCEDURE). */
    static String resolveOrosOrderType(String category) {
        if (category == null || category.isBlank()) {
            return "LAB";
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("IMAG") || normalized.contains("RADIO") || normalized.equals("CT")
                || normalized.equals("MRI") || normalized.equals("XRAY")) {
            return "IMAGING";
        }
        if (normalized.contains("PHARM")) {
            return "PHARMACY";
        }
        if (normalized.contains("PROC")) {
            return "PROCEDURE";
        }
        return "LAB";
    }
}
