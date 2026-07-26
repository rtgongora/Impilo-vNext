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
            @RequestParam(required = false, name = "encounter_id") String encounterId,
            @RequestParam(required = false, name = "status") String status) {
        // Encounter Orders & Results panel: orders linked to a specific encounter.
        if (encounterId != null && !encounterId.isBlank()) {
            try {
                JsonNode orosData = orosClient.listOrdersByEncounter(encounterId);
                if (orosData != null) {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("data", orosData);
                    response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                    return ResponseEntity.ok(response);
                }
            } catch (Exception e) {
                // Falling through to the empty list below would report "no orders on this
                // encounter" — the finding a clinician uses to decide nothing was sent to the lab.
                log.error("OROS listOrdersByEncounter failed for encounter={}: {}",
                        encounterId, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "error", "lab_orders_unavailable",
                        "message", "Lab orders could not be retrieved. Do not treat this as an "
                                   + "absence of orders or results.",
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        }
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
                // As above — an empty list here is an affirmative "nothing pending for this
                // patient", including no unreviewed critical results.
                log.error("OROS getPatientOrders failed for patient={}: {}",
                        patientId, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "error", "lab_orders_unavailable",
                        "message", "Lab orders could not be retrieved. Do not treat this as an "
                                   + "absence of orders or results.",
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
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
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        JsonNode orderData = orosClient.getOrder(id);

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

        // The canonical id IS the OROS order id when placement succeeded, so every follow-on action
        // (collect/result/acknowledge/cancel) addresses the sovereign order — not a BFF-local UUID
        // that OROS never knew about. Falls back to the local id only if OROS placement failed.
        String canonicalId = orosOrderId != null ? orosOrderId : orderId.toString();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", canonicalId,
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
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        // Real specimen collection on the sovereign order (OROS specimen lifecycle), not a stub.
        String status = "COLLECTED";
        try {
            orosClient.collectSpecimen(id, body);
            log.info("OROS specimen collected for order={}", id);
        } catch (Exception e) {
            // Category (b): defensible. Unlike the result/acknowledge/cancel paths, this does not
            // claim the transition happened — it returns a distinct COLLECT_PENDING status the
            // caller can act on, so the 200 body is not a fabricated success.
            log.warn("OROS specimen collect failed, reporting COLLECT_PENDING: {}", e.getMessage());
            status = "COLLECT_PENDING";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id, "status", status));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/result")
    public ResponseEntity<Map<String, Object>> resultLabOrder(
            @PathVariable String id,
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
                orosClient.postResult(id, "LAB", body.get("result_data"), hasCritical);
                log.info("OROS result posted for order={}", id);
            } catch (Exception e) {
                // "non-blocking" meant the response still claimed status RESULTED for a result
                // OROS never stored — including critical results. The order is the system of
                // record; if the write did not land, the caller must not be told it did.
                log.error("OROS result delegation failed for order={}: {}", id, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "error", "lab_result_not_recorded",
                        "message", "The result could not be recorded against the order. It has "
                                   + "not been saved — do not treat it as filed.",
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id, "status", "RESULTED"));
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
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        String notes = body != null && body.containsKey("notes") ? (String) body.get("notes") : null;

        try {
            orosClient.acknowledgeOrder(id, "CLINICIAN", notes);
            log.info("OROS acknowledgement posted for order={}", id);
        } catch (Exception e) {
            // Acknowledgement is the audit trail for "a clinician has seen this result". A 200
            // reporting REVIEWED when OROS rejected the write leaves the result unreviewed in the
            // record while the ward believes it was signed off.
            log.error("OROS acknowledgement failed for order={}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "lab_acknowledgement_not_recorded",
                    "message", "The acknowledgement could not be recorded. This result is still "
                               + "unreviewed.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id, "status", "REVIEWED"));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelLabOrder(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        String reason = body != null && body.containsKey("reason") ? (String) body.get("reason") : null;

        try {
            orosClient.cancelOrder(id,
                    reason != null ? reason : "Cancelled from experience UI");
            log.info("OROS order cancelled for lab order={}", id);
        } catch (Exception e) {
            // A 200 reporting CANCELLED for an order OROS still holds as active means the
            // specimen keeps moving through the lab after the clinician believes they stopped it.
            log.error("OROS cancel failed for order={}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "lab_order_not_cancelled",
                    "message", "The order could not be cancelled and is still active.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id, "status", "CANCELLED"));
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
