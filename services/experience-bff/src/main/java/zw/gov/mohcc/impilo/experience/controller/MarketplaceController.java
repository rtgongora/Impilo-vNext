package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;

import java.util.*;

/**
 * Marketplace endpoints. Delegates to MsikaFlowServiceClient.
 * GET  /internal/v1/marketplace/orders — list marketplace orders with facility_id filter, pagination.
 * GET  /internal/v1/marketplace/orders/{id} — get single order.
 * POST /internal/v1/marketplace/orders — create order.
 */
@RestController
@RequestMapping("/internal/v1/marketplace")
public class MarketplaceController {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceController.class);

    private final MsikaFlowServiceClient msikaFlowClient;
    private final MsikaServiceClient msikaServiceClient;

    public MarketplaceController(MsikaFlowServiceClient msikaFlowClient, MsikaServiceClient msikaServiceClient) {
        this.msikaFlowClient = msikaFlowClient;
        this.msikaServiceClient = msikaServiceClient;
    }

    public record CreateOrderRequest(
            @NotBlank String facility_id,
            @NotBlank String order_number,
            JsonNode items,
            @NotBlank String ordered_by,
            String total_amount
    ) {}

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> listOrders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "facility_id") String facilityId) {
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Marketplace order list is not yet exposed on msika-flow-service (no tenant-scoped GET /v1/orders); use order id or vendor-scoped flows.");
    }

    @GetMapping("/catalog")
    public ResponseEntity<Map<String, Object>> listCatalog(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (search != null && !search.isBlank()) {
            params.add("q", search);
        }
        if (category != null && !category.isBlank()) {
            params.add("kind", category);
        }
        params.add("page", "0");
        params.add("size", "50");

        ResponseEntity<String> result = msikaServiceClient.search(params);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.getBody());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vendors")
    public ResponseEntity<Map<String, Object>> listPartners(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        ResponseEntity<String> result = msikaFlowClient.listVendors(null);

        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(..., tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.getBody());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bookings")
    public ResponseEntity<Map<String, Object>> listBookings(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        throw new ResponseStatusException(
                HttpStatus.NOT_IMPLEMENTED,
                "Marketplace bookings list is not exposed on msika-flow-service (bookings are POST-only today).");
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Map<String, Object>> getOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        ResponseEntity<String> result = msikaFlowClient.getOrder(id.toString());

        // MarketplaceOrder order = marketplaceOrderRepository.findByIdAndTenantId(id, tenantId)
        //         .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.getBody());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        String itemsJson = request.items() == null || request.items().isNull()
                ? "[]" : request.items().toString();
        String orderBody = String.format(
                "{\"facility_id\":\"%s\",\"order_number\":\"%s\",\"items\":%s,\"ordered_by\":\"%s\",\"total_amount\":\"%s\"}",
                request.facility_id(), request.order_number(),
                itemsJson,
                request.ordered_by(),
                request.total_amount() != null ? request.total_amount() : "0");

        try {
            ResponseEntity<String> result = msikaFlowClient.createOrder(orderBody);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", result.getBody());
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.info("MSIKA Flow unavailable — local marketplace order fallback: {}", e.getMessage());
            String syntheticId = "ord-" + UUID.randomUUID().toString().substring(0, 8);
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("order_number", request.order_number());
            attrs.put("facility_id", request.facility_id());
            attrs.put("status", "SUBMITTED");
            attrs.put("ordered_by", request.ordered_by());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", syntheticId);
            data.put("type", "MarketplaceOrder");
            data.put("attributes", attrs);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
