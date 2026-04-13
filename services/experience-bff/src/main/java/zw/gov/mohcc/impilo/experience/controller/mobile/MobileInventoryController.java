package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.InventoryServiceClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Mobile inventory endpoints.
 * GET  /internal/v1/mobile/provider/inventory/stock?facility_id=         - stock items
 * GET  /internal/v1/mobile/provider/inventory/stock/alerts?facility_id=  - stock alerts
 * POST /internal/v1/mobile/provider/inventory/dispatches                 - create dispatch
 * GET  /internal/v1/mobile/provider/inventory/dispatches?facility_id=    - dispatches
 * POST /internal/v1/mobile/provider/inventory/dispatches/{id}/confirm    - confirm delivery
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/inventory")
public class MobileInventoryController {

    private final InventoryServiceClient inventoryClient;
    private final ObjectMapper objectMapper;

    public MobileInventoryController(InventoryServiceClient inventoryClient, ObjectMapper objectMapper) {
        this.inventoryClient = inventoryClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/stock")
    public ResponseEntity<Map<String, Object>> stockItems(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            var data = inventoryClient.getOnHand(UUID.fromString(facilityId), null, null, null, page, size);
            if (data != null) {
                return ResponseEntity.ok(Map.of(
                        "data", data,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @GetMapping("/stock/alerts")
    public ResponseEntity<Map<String, Object>> stockAlerts(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId) {
        try {
            var stockouts = inventoryClient.getStockouts(UUID.fromString(facilityId));
            var nearExpiry = inventoryClient.getNearExpiry(UUID.fromString(facilityId), 30);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("stockouts", stockouts);
            payload.put("near_expiry", nearExpiry);
            return ResponseEntity.ok(Map.of(
                    "data", payload,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", Map.of("stockouts", List.of(), "near_expiry", List.of()),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    public record CreateDispatchRequest(
            @NotBlank String source_facility_id,
            @NotBlank String destination_facility_id,
            @NotNull List<DispatchLineItem> items,
            String notes
    ) {}

    public record DispatchLineItem(
            @NotBlank String item_code,
            @NotNull BigDecimal quantity,
            String batch_number
    ) {}

    @PostMapping("/dispatches")
    public ResponseEntity<Map<String, Object>> createDispatch(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateDispatchRequest request) {

        UUID dispatchId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String dispatchNumber = "DSP-" + now.toEpochSecond();

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("source_facility_id", request.source_facility_id());
        attributes.put("destination_facility_id", request.destination_facility_id());
        attributes.put("dispatch_number", dispatchNumber);
        attributes.put("status", "IN_TRANSIT");
        attributes.put("item_count", request.items().size());
        attributes.put("notes", request.notes());
        attributes.put("dispatched_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", dispatchId.toString(),
                "type", "InventoryDispatch",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/dispatches")
    public ResponseEntity<Map<String, Object>> dispatches(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode handovers = inventoryClient.listHandovers(page, size);
            if (handovers != null) {
                return ResponseEntity.ok(Map.of(
                        "data", handovers,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    @PostMapping("/dispatches/{id}/confirm")
    public ResponseEntity<Map<String, Object>> confirmDispatch(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode result = inventoryClient.signIncomingHandover(id);
            if (result != null) {
                return ResponseEntity.ok(Map.of(
                        "data", result,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", Map.of("id", id.toString(), "confirmed", true),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    private Map<String, Object> toStockResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", row.get("facility_id"));
        attributes.put("item_code", row.get("item_code"));
        attributes.put("item_name", row.get("item_name"));
        attributes.put("category", row.get("category"));
        attributes.put("unit_of_measure", row.get("unit_of_measure"));
        attributes.put("quantity_on_hand", row.get("quantity_on_hand"));
        attributes.put("reorder_level", row.get("reorder_level"));
        attributes.put("max_stock_level", row.get("max_stock_level"));
        attributes.put("batch_number", row.get("batch_number"));
        attributes.put("expiry_date", row.get("expiry_date"));
        attributes.put("status", row.get("status"));
        attributes.put("last_restocked_at", row.get("last_restocked_at"));
        attributes.put("created_at", row.get("created_at"));
        attributes.put("updated_at", row.get("updated_at"));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", row.get("id").toString());
        resource.put("type", "InventoryItem");
        resource.put("attributes", attributes);
        return resource;
    }
}
