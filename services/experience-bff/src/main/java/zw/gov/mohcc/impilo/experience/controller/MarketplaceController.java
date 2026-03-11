package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.MarketplaceOrder;
import zw.gov.mohcc.impilo.experience.repository.MarketplaceOrderRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Marketplace endpoints.
 * GET  /internal/v1/marketplace/orders — list marketplace orders with facility_id filter, pagination.
 * GET  /internal/v1/marketplace/orders/{id} — get single order.
 * POST /internal/v1/marketplace/orders — create order.
 */
@RestController
@RequestMapping("/internal/v1/marketplace")
public class MarketplaceController {

    private final MarketplaceOrderRepository marketplaceOrderRepository;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;

    public MarketplaceController(MarketplaceOrderRepository marketplaceOrderRepository,
                                 OutboxService outboxService,
                                 JdbcTemplate jdbcTemplate) {
        this.marketplaceOrderRepository = marketplaceOrderRepository;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CreateOrderRequest(
            @NotBlank String facility_id,
            @NotBlank String order_number,
            String items,
            @NotBlank String ordered_by
    ) {}

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> listOrders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "facility_id") String facilityId) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());

        Page<MarketplaceOrder> result;
        if (facilityId != null) {
            result = marketplaceOrderRepository.findByTenantIdAndFacilityId(tenantId, facilityId, pageable);
        } else {
            result = marketplaceOrderRepository.findAll(pageable);
        }

        List<Map<String, Object>> data = result.getContent().stream()
                .map(this::toResource)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", result.getNumber(),
                        "size", result.getSize(),
                        "total_elements", result.getTotalElements(),
                        "total_pages", result.getTotalPages()
                )
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Map<String, Object>> getOrder(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        MarketplaceOrder order = marketplaceOrderRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(order));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

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

        UUID orderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update("""
            INSERT INTO marketplace_orders
                (id, tenant_id, facility_id, order_number, items, ordered_by, status,
                 ordered_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?, ?::jsonb, ?, 'PENDING', ?, ?, ?)
            """,
                orderId, tenantId, request.facility_id(),
                request.order_number(), request.items(),
                request.ordered_by(),
                now, now, now);

        outboxService.writeOutboxEvent(
                "impilo.experience.marketplace.order-created.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "MarketplaceOrder",
                orderId.toString(),
                Map.of(
                        "order_id", orderId.toString(),
                        "facility_id", request.facility_id(),
                        "order_number", request.order_number(),
                        "ordered_by", request.ordered_by(),
                        "status", "PENDING"
                ),
                Map.of()
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", request.facility_id());
        attributes.put("order_number", request.order_number());
        attributes.put("items", request.items());
        attributes.put("ordered_by", request.ordered_by());
        attributes.put("status", "PENDING");
        attributes.put("ordered_at", now);
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", orderId.toString(),
                "type", "MarketplaceOrder",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private Map<String, Object> toResource(MarketplaceOrder o) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", o.getFacilityId());
        attributes.put("vendor_id", o.getVendorId());
        attributes.put("order_number", o.getOrderNumber());
        attributes.put("status", o.getStatus());
        attributes.put("total_amount", o.getTotalAmount());
        attributes.put("currency", o.getCurrency());
        attributes.put("items", o.getItems());
        attributes.put("ordered_by", o.getOrderedBy());
        attributes.put("ordered_at", o.getOrderedAt());
        attributes.put("delivered_at", o.getDeliveredAt());
        attributes.put("created_at", o.getCreatedAt());
        attributes.put("updated_at", o.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", o.getId().toString());
        resource.put("type", "MarketplaceOrder");
        resource.put("attributes", attributes);
        return resource;
    }
}
