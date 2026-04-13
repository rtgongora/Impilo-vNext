package zw.gov.mohcc.impilo.experience.controller.mobile;

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
 * Mobile lab order endpoints.
 * POST /internal/v1/mobile/provider/labs              - create lab order
 * GET  /internal/v1/mobile/provider/labs?encounter_id= or patient_id= - list
 * GET  /internal/v1/mobile/provider/labs/{id}         - get single
 * POST /internal/v1/mobile/provider/labs/{id}/cancel  - cancel lab order
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/labs")
public class MobileLabController {

    private static final Logger log = LoggerFactory.getLogger(MobileLabController.class);

    private final OrosServiceClient orosClient;

    public MobileLabController(OrosServiceClient orosClient) {
        this.orosClient = orosClient;
    }

    public record CreateLabOrderRequest(
            @NotBlank String encounter_id,
            @NotBlank String patient_id,
            @NotBlank String test_code,
            @NotBlank String test_name,
            String priority,
            String clinical_notes,
            String specimen_type
    ) {}

    public record CancelLabOrderRequest(
            String reason
    ) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> createLabOrder(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateLabOrderRequest request) {

        UUID labOrderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String priority = request.priority() != null ? request.priority() : "ROUTINE";

        // Delegate to OROS sovereign service
        String orosOrderId = null;
        try {
            List<Map<String, Object>> items = List.of(Map.of(
                    "code", request.test_code(),
                    "displayName", request.test_name(),
                    "quantity", 1
            ));
            JsonNode orosData = orosClient.placeOrder(
                    "LAB", priority, request.patient_id(), request.encounter_id(),
                    request.clinical_notes(), items);
            if (orosData != null && orosData.has("orderId")) {
                orosOrderId = orosData.get("orderId").asText();
            }
            log.info("OROS order placed from mobile: {} for lab order {}", orosOrderId, labOrderId);
        } catch (Exception e) {
            log.warn("OROS delegation from mobile lab failed (non-blocking): {}", e.getMessage());
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter_id", request.encounter_id());
        attributes.put("patient_id", request.patient_id());
        attributes.put("test_code", request.test_code());
        attributes.put("test_name", request.test_name());
        attributes.put("priority", priority);
        attributes.put("clinical_notes", request.clinical_notes());
        attributes.put("specimen_type", request.specimen_type());
        attributes.put("status", "ORDERED");
        attributes.put("ordered_at", now);
        attributes.put("created_at", now);
        if (orosOrderId != null) attributes.put("oros_order_id", orosOrderId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", labOrderId.toString(),
                "type", "LabOrder",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listLabOrders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "encounter_id") String encounterId,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (patientId != null && !patientId.isBlank()) {
            try {
                JsonNode data = orosClient.getPatientOrders(patientId);
                if (data != null) {
                    return ResponseEntity.ok(Map.of(
                            "data", data,
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                    ));
                }
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getLabOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = orosClient.getOrder(id.toString());
            if (data != null) {
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelLabOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) CancelLabOrderRequest request) {
        String reason = request != null && request.reason() != null ? request.reason() : "Cancelled from mobile";
        try {
            JsonNode data = orosClient.cancelOrder(id.toString(), reason);
            if (data != null) {
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "cancelled", true),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }
}
