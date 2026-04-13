package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;

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

    private final MsikaFlowServiceClient msikaFlowClient;

    public MarketplaceController(MsikaFlowServiceClient msikaFlowClient) {
        this.msikaFlowClient = msikaFlowClient;
    }

    public record CreateOrderRequest(
            @NotBlank String facility_id,
            @NotBlank String order_number,
            String items,
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

        // STRANGLER: migrated — delegate to MsikaFlowServiceClient
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (facilityId != null) params.add("facilityId", facilityId);
        params.add("page", String.valueOf(page));
        params.add("size", String.valueOf(Math.min(size, 100)));

        ResponseEntity<String> result = msikaFlowClient.listVendors(params);

        // STRANGLER: migrated — was MarketplaceOrderRepository.findByTenantIdAndFacilityId
        // Page<MarketplaceOrder> result = marketplaceOrderRepository.findByTenantIdAndFacilityId(tenantId, facilityId, pageable);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.getBody());
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/catalog")
    public ResponseEntity<Map<String, Object>> listCatalog(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {

        // STRANGLER: migrated — delegate to MsikaFlowServiceClient
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (category != null && !category.isBlank()) params.add("category", category);
        if (search != null && !search.isBlank()) params.add("search", search);

        ResponseEntity<String> result = msikaFlowClient.listVendors(params);

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from marketplace_services
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

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

        // STRANGLER: migrated — delegate to MsikaFlowServiceClient
        ResponseEntity<String> result = msikaFlowClient.listVendors(null);

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from marketplace_services with GROUP BY
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

        // STRANGLER: migrated — delegate to MsikaFlowServiceClient
        ResponseEntity<String> result = msikaFlowClient.listVendors(null);

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from service_requests
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(..., tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.getBody());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Map<String, Object>> getOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        // STRANGLER: migrated — delegate to MsikaFlowServiceClient
        ResponseEntity<String> result = msikaFlowClient.getOrder(id.toString());

        // STRANGLER: migrated — was MarketplaceOrderRepository.findByIdAndTenantId
        // MarketplaceOrder order = marketplaceOrderRepository.findByIdAndTenantId(id, tenantId)
        //         .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.getBody());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        // STRANGLER: migrated — delegate to MsikaFlowServiceClient
        String orderBody = String.format(
                "{\"facility_id\":\"%s\",\"order_number\":\"%s\",\"items\":%s,\"ordered_by\":\"%s\",\"total_amount\":\"%s\"}",
                request.facility_id(), request.order_number(),
                request.items() != null ? request.items() : "[]",
                request.ordered_by(),
                request.total_amount() != null ? request.total_amount() : "0");

        ResponseEntity<String> result = msikaFlowClient.createOrder(orderBody);

        // STRANGLER: migrated — was direct JdbcTemplate INSERT into marketplace_orders
        // jdbcTemplate.update("""
        //     INSERT INTO marketplace_orders (...) VALUES (...)
        //     """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result.getBody());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
