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
        String priority = request.priority() != null ? request.priority() : "ROUTINE";

        try {
            List<Map<String, Object>> items = List.of(Map.of(
                    "code", request.test_code(),
                    "displayName", request.test_name(),
                    "quantity", 1
            ));
            JsonNode orosData = orosClient.placeOrder(
                    "LAB", priority, request.patient_id(), request.encounter_id(),
                    request.clinical_notes(), items);
            if (orosData == null) {
                return upstreamFailure("OROS_UNAVAILABLE", "No order payload returned", requestId, correlationId);
            }
            log.info("OROS order placed from mobile lab flow");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", orosData,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("OROS delegation from mobile lab failed: {}", e.getMessage());
            return upstreamFailure("OROS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
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
                    return ResponseEntity.ok(Map.of("data", data));
                }
                return upstreamFailure("OROS_UNAVAILABLE", "No order list payload returned", requestId, correlationId);
            } catch (Exception e) {
                return upstreamFailure("OROS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", Map.of("code", "MISSING_PATIENT_ID", "message", "patient_id query parameter is required"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
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
                return ResponseEntity.ok(Map.of("data", data));
            }
            return upstreamFailure("OROS_UNAVAILABLE", "No order payload returned", requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("OROS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
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
                return ResponseEntity.ok(Map.of("data", data));
            }
            return upstreamFailure("OROS_UNAVAILABLE", "No cancel payload returned", requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("OROS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Mobile lab upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
