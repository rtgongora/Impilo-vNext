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

    @GetMapping("/catalog")
    public ResponseEntity<Map<String, Object>> listCatalog(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {

        StringBuilder sql = new StringBuilder("""
            SELECT id, name, description, category, facility_id, facility_name,
                   price, currency, available, rating, image_url
            FROM marketplace_services
            WHERE tenant_id = ?
            """);
        List<Object> params = new ArrayList<>(List.of(tenantId));

        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(name) LIKE ? OR LOWER(description) LIKE ?)");
            String like = "%" + search.toLowerCase(Locale.ROOT) + "%";
            params.add(like);
            params.add(like);
        }

        sql.append(" ORDER BY available DESC, name ASC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<Map<String, Object>> data = rows.stream().map(this::toCatalogResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vendors")
    public ResponseEntity<Map<String, Object>> listPartners(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT facility_id, facility_name,
                   count(*) AS service_count,
                   sum(CASE WHEN available THEN 1 ELSE 0 END) AS active_listings,
                   avg(COALESCE(rating, 0)) AS average_rating
            FROM marketplace_services
            WHERE tenant_id = ?
            GROUP BY facility_id, facility_name
            ORDER BY facility_name
            """, tenantId);

        List<Map<String, Object>> data = rows.stream().map(this::toPartnerResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bookings")
    public ResponseEntity<Map<String, Object>> listBookings(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT sr.id, sr.service_name, sr.facility_name, sr.status,
                   sr.requested_at, sr.scheduled_at, sr.completed_at, sr.tracking_number
            FROM service_requests sr
            WHERE sr.tenant_id = ?
            ORDER BY COALESCE(sr.scheduled_at, sr.requested_at) DESC
            LIMIT 50
            """, tenantId);

        List<Map<String, Object>> data = rows.stream().map(this::toBookingResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
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
                 total_amount, ordered_at, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?, ?::jsonb, ?, 'PENDING', ?::numeric, ?, ?, ?)
            """,
                orderId, tenantId, request.facility_id(),
                request.order_number(), request.items(),
                request.ordered_by(),
                request.total_amount(),
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
        attributes.put("total_amount", request.total_amount());
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

    private Map<String, Object> toCatalogResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", row.get("name"));
        attributes.put("description", row.get("description"));
        attributes.put("category", row.get("category"));
        attributes.put("facility_id", row.get("facility_id") != null ? row.get("facility_id").toString() : null);
        attributes.put("facility_name", row.get("facility_name"));
        attributes.put("price", row.get("price"));
        attributes.put("currency", row.get("currency"));
        attributes.put("availability", Boolean.TRUE.equals(row.get("available")) ? "AVAILABLE" : "UNAVAILABLE");
        attributes.put("rating", row.get("rating"));
        attributes.put("image_url", row.get("image_url"));
        return Map.of(
                "id", row.get("id").toString(),
                "type", "MarketplaceCatalogItem",
                "attributes", attributes
        );
    }

    private Map<String, Object> toPartnerResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", row.get("facility_name"));
        attributes.put("facility_id", row.get("facility_id") != null ? row.get("facility_id").toString() : null);
        attributes.put("service_count", row.get("service_count"));
        attributes.put("active_listings", row.get("active_listings"));
        attributes.put("status", ((Number) row.get("active_listings")).intValue() > 0 ? "ACTIVE" : "INACTIVE");
        attributes.put("rating", row.get("average_rating"));
        return Map.of(
                "id", row.get("facility_id") != null ? row.get("facility_id").toString() : UUID.randomUUID().toString(),
                "type", "MarketplacePartner",
                "attributes", attributes
        );
    }

    private Map<String, Object> toBookingResource(Map<String, Object> row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("booking_number", row.get("tracking_number"));
        attributes.put("service_name", row.get("service_name"));
        attributes.put("provider_name", row.get("facility_name"));
        attributes.put("requested_at", row.get("requested_at"));
        attributes.put("scheduled_at", row.get("scheduled_at"));
        attributes.put("completed_at", row.get("completed_at"));
        attributes.put("status", row.get("status"));
        return Map.of(
                "id", row.get("id").toString(),
                "type", "MarketplaceBooking",
                "attributes", attributes
        );
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
