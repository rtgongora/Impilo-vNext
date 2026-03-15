package zw.gov.mohcc.impilo.experience.controller.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

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

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public MobileInventoryController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
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

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows;
        Long total;

        if (category != null) {
            rows = jdbcTemplate.queryForList("""
                SELECT id, facility_id, item_code, item_name, category, unit_of_measure,
                       quantity_on_hand, reorder_level, max_stock_level, batch_number,
                       expiry_date, status, last_restocked_at, created_at, updated_at
                FROM inventory_items
                WHERE tenant_id = ? AND facility_id = ?::uuid AND category = ?
                ORDER BY item_name ASC
                LIMIT ? OFFSET ?
                """, tenantId, facilityId, category, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM inventory_items
                WHERE tenant_id = ? AND facility_id = ?::uuid AND category = ?
                """, Long.class, tenantId, facilityId, category);
        } else {
            rows = jdbcTemplate.queryForList("""
                SELECT id, facility_id, item_code, item_name, category, unit_of_measure,
                       quantity_on_hand, reorder_level, max_stock_level, batch_number,
                       expiry_date, status, last_restocked_at, created_at, updated_at
                FROM inventory_items
                WHERE tenant_id = ? AND facility_id = ?::uuid
                ORDER BY item_name ASC
                LIMIT ? OFFSET ?
                """, tenantId, facilityId, limit, offset);
            total = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM inventory_items WHERE tenant_id = ? AND facility_id = ?::uuid
                """, Long.class, tenantId, facilityId);
        }

        List<Map<String, Object>> data = rows.stream().map(this::toStockResource).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page,
                        "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0
                )
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stock/alerts")
    public ResponseEntity<Map<String, Object>> stockAlerts(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId) {

        List<Map<String, Object>> lowStockRows = jdbcTemplate.queryForList("""
            SELECT id, facility_id, item_code, item_name, category, unit_of_measure,
                   quantity_on_hand, reorder_level, max_stock_level, batch_number,
                   expiry_date, status, last_restocked_at, created_at, updated_at
            FROM inventory_items
            WHERE tenant_id = ? AND facility_id = ?::uuid
              AND quantity_on_hand <= reorder_level
            ORDER BY quantity_on_hand ASC
            """, tenantId, facilityId);

        List<Map<String, Object>> expiringRows = jdbcTemplate.queryForList("""
            SELECT id, facility_id, item_code, item_name, category, unit_of_measure,
                   quantity_on_hand, reorder_level, max_stock_level, batch_number,
                   expiry_date, status, last_restocked_at, created_at, updated_at
            FROM inventory_items
            WHERE tenant_id = ? AND facility_id = ?::uuid
              AND expiry_date IS NOT NULL
              AND expiry_date <= CURRENT_DATE + INTERVAL '90 days'
            ORDER BY expiry_date ASC
            """, tenantId, facilityId);

        List<Map<String, Object>> lowStock = lowStockRows.stream().map(row -> {
            Map<String, Object> resource = toStockResource(row);
            ((Map<String, Object>) resource.get("attributes")).put("alert_type", "LOW_STOCK");
            return resource;
        }).toList();

        List<Map<String, Object>> expiring = expiringRows.stream().map(row -> {
            Map<String, Object> resource = toStockResource(row);
            ((Map<String, Object>) resource.get("attributes")).put("alert_type", "EXPIRING");
            return resource;
        }).toList();

        List<Map<String, Object>> allAlerts = new ArrayList<>();
        allAlerts.addAll(lowStock);
        allAlerts.addAll(expiring);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", allAlerts);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "low_stock_count", lowStock.size(),
                "expiring_count", expiring.size()
        ));

        return ResponseEntity.ok(response);
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
    @Transactional
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

        jdbcTemplate.update("""
            INSERT INTO inventory_dispatches
                (id, tenant_id, source_facility_id, destination_facility_id, dispatch_number,
                 status, dispatched_at, notes, created_at, updated_at)
            VALUES (?, ?, ?::uuid, ?::uuid, ?, 'IN_TRANSIT', ?, ?, ?, ?)
            """,
                dispatchId, tenantId, request.source_facility_id(),
                request.destination_facility_id(), dispatchNumber,
                now, request.notes(), now, now);

        for (DispatchLineItem item : request.items()) {
            UUID lineId = UUID.randomUUID();
            jdbcTemplate.update("""
                INSERT INTO dispatch_line_items
                    (id, dispatch_id, item_code, quantity, batch_number, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                    lineId, dispatchId, item.item_code(), item.quantity(),
                    item.batch_number(), now);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.dispatch.created.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "InventoryDispatch",
                dispatchId.toString(),
                Map.of(
                        "dispatch_id", dispatchId.toString(),
                        "source_facility_id", request.source_facility_id(),
                        "destination_facility_id", request.destination_facility_id(),
                        "dispatch_number", dispatchNumber,
                        "item_count", String.valueOf(request.items().size()),
                        "status", "IN_TRANSIT"
                ),
                Map.of()
        );

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

        int limit = Math.min(size, 100);
        int offset = page * limit;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, source_facility_id, destination_facility_id, dispatch_number,
                   status, dispatched_at, expected_delivery_at, confirmed_at,
                   confirmed_by, notes, created_at, updated_at
            FROM inventory_dispatches
            WHERE tenant_id = ? AND destination_facility_id = ?::uuid
            ORDER BY dispatched_at DESC
            LIMIT ? OFFSET ?
            """, tenantId, facilityId, limit, offset);

        Long total = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM inventory_dispatches
            WHERE tenant_id = ? AND destination_facility_id = ?::uuid
            """, Long.class, tenantId, facilityId);

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("source_facility_id", row.get("source_facility_id"));
            attributes.put("destination_facility_id", row.get("destination_facility_id"));
            attributes.put("dispatch_number", row.get("dispatch_number"));
            attributes.put("status", row.get("status"));
            attributes.put("dispatched_at", row.get("dispatched_at"));
            attributes.put("expected_delivery_at", row.get("expected_delivery_at"));
            attributes.put("confirmed_at", row.get("confirmed_at"));
            attributes.put("confirmed_by", row.get("confirmed_by"));
            attributes.put("notes", row.get("notes"));
            attributes.put("created_at", row.get("created_at"));
            attributes.put("updated_at", row.get("updated_at"));

            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("id", row.get("id").toString());
            resource.put("type", "InventoryDispatch");
            resource.put("attributes", attributes);
            return resource;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", page,
                        "size", limit,
                        "total_elements", total != null ? total : 0L,
                        "total_pages", total != null ? (int) Math.ceil((double) total / limit) : 0
                )
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/dispatches/{id}/confirm")
    @Transactional
    public ResponseEntity<Map<String, Object>> confirmDispatch(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        OffsetDateTime now = OffsetDateTime.now();
        String confirmedBy = body != null && body.containsKey("confirmed_by")
                ? body.get("confirmed_by").toString()
                : "system";
        String notes = body != null && body.containsKey("notes")
                ? body.get("notes").toString()
                : null;

        int updated = jdbcTemplate.update("""
            UPDATE inventory_dispatches
            SET status = 'CONFIRMED', confirmed_at = ?, confirmed_by = ?,
                notes = COALESCE(?, notes), updated_at = ?
            WHERE id = ? AND tenant_id = ? AND status = 'IN_TRANSIT'
            """, now, confirmedBy, notes, now, id, tenantId);

        if (updated == 0) {
            throw new ResourceNotFoundException("In-transit dispatch not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.dispatch.confirmed.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "InventoryDispatch",
                id.toString(),
                Map.of(
                        "dispatch_id", id.toString(),
                        "status", "CONFIRMED",
                        "confirmed_by", confirmedBy
                ),
                Map.of()
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, source_facility_id, destination_facility_id, dispatch_number,
                   status, dispatched_at, expected_delivery_at, confirmed_at,
                   confirmed_by, notes, created_at, updated_at
            FROM inventory_dispatches
            WHERE id = ? AND tenant_id = ?
            """, id, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("source_facility_id", row.get("source_facility_id"));
            attributes.put("destination_facility_id", row.get("destination_facility_id"));
            attributes.put("dispatch_number", row.get("dispatch_number"));
            attributes.put("status", row.get("status"));
            attributes.put("dispatched_at", row.get("dispatched_at"));
            attributes.put("expected_delivery_at", row.get("expected_delivery_at"));
            attributes.put("confirmed_at", row.get("confirmed_at"));
            attributes.put("confirmed_by", row.get("confirmed_by"));
            attributes.put("notes", row.get("notes"));
            attributes.put("created_at", row.get("created_at"));
            attributes.put("updated_at", row.get("updated_at"));

            response.put("data", Map.of(
                    "id", row.get("id").toString(),
                    "type", "InventoryDispatch",
                    "attributes", attributes
            ));
        } else {
            response.put("data", Map.of());
        }
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
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
