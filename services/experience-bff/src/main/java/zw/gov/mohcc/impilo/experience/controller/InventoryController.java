package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.InventoryItem;
import zw.gov.mohcc.impilo.experience.repository.InventoryItemRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Inventory endpoints.
 * GET /internal/v1/inventory/items — list inventory items with facility_id filter, pagination.
 */
@RestController
@RequestMapping("/internal/v1/inventory")
public class InventoryController {

    private final InventoryItemRepository inventoryItemRepository;
    private final JdbcTemplate jdbcTemplate;

    public InventoryController(InventoryItemRepository inventoryItemRepository, JdbcTemplate jdbcTemplate) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CreateRequisitionRequest(
            String facility_id,
            String requisition_number,
            String requested_by,
            Integer item_count,
            String needed_by,
            String notes
    ) {}

    @GetMapping("/items")
    public ResponseEntity<Map<String, Object>> listItems(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "facility_id") String facilityId) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("productName").ascending());

        Page<InventoryItem> result;
        if (facilityId != null) {
            result = inventoryItemRepository.findByTenantIdAndFacilityId(tenantId, facilityId, pageable);
        } else {
            result = inventoryItemRepository.findAll(pageable);
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

    @GetMapping("/counts")
    public ResponseEntity<Map<String, Object>> listCounts(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "facility_id") String facilityId) {

        String sql = """
            SELECT id, count_date, counted_by, item_count, discrepancies, status, notes
            FROM inventory_stock_counts
            WHERE tenant_id = ?
            """ + (facilityId != null ? " AND facility_id = ?::uuid" : "") + """
            ORDER BY count_date DESC
            LIMIT 20
            """;

        List<Map<String, Object>> rows = facilityId != null
                ? jdbcTemplate.queryForList(sql, tenantId, facilityId)
                : jdbcTemplate.queryForList(sql, tenantId);

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("count_date", row.get("count_date"));
            attributes.put("counted_by", row.get("counted_by"));
            attributes.put("item_count", row.get("item_count"));
            attributes.put("discrepancies", row.get("discrepancies"));
            attributes.put("status", row.get("status"));
            attributes.put("notes", row.get("notes"));
            return Map.of(
                    "id", row.get("id").toString(),
                    "type", "InventoryStockCount",
                    "attributes", attributes
            );
        }).toList();

        return ResponseEntity.ok(response(data, requestId, correlationId));
    }

    @GetMapping("/movements")
    public ResponseEntity<Map<String, Object>> listMovements(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "facility_id") String facilityId) {

        String sql = """
            SELECT id, moved_at, item_name, from_location, to_location, quantity, reason, performed_by
            FROM inventory_movements
            WHERE tenant_id = ?
            """ + (facilityId != null ? " AND facility_id = ?::uuid" : "") + """
            ORDER BY moved_at DESC
            LIMIT 50
            """;

        List<Map<String, Object>> rows = facilityId != null
                ? jdbcTemplate.queryForList(sql, tenantId, facilityId)
                : jdbcTemplate.queryForList(sql, tenantId);

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("moved_at", row.get("moved_at"));
            attributes.put("item_name", row.get("item_name"));
            attributes.put("from_location", row.get("from_location"));
            attributes.put("to_location", row.get("to_location"));
            attributes.put("quantity", row.get("quantity"));
            attributes.put("reason", row.get("reason"));
            attributes.put("performed_by", row.get("performed_by"));
            return Map.of(
                    "id", row.get("id").toString(),
                    "type", "InventoryMovement",
                    "attributes", attributes
            );
        }).toList();

        return ResponseEntity.ok(response(data, requestId, correlationId));
    }

    @GetMapping("/requisitions")
    public ResponseEntity<Map<String, Object>> listRequisitions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "facility_id") String facilityId) {

        String sql = """
            SELECT id, requisition_number, requested_by, item_count, status, needed_by, notes, created_at
            FROM inventory_requisitions
            WHERE tenant_id = ?
            """ + (facilityId != null ? " AND facility_id = ?::uuid" : "") + """
            ORDER BY created_at DESC
            LIMIT 50
            """;

        List<Map<String, Object>> rows = facilityId != null
                ? jdbcTemplate.queryForList(sql, tenantId, facilityId)
                : jdbcTemplate.queryForList(sql, tenantId);

        List<Map<String, Object>> data = rows.stream().map(row -> {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("requisition_number", row.get("requisition_number"));
            attributes.put("requested_by", row.get("requested_by"));
            attributes.put("item_count", row.get("item_count"));
            attributes.put("status", row.get("status"));
            attributes.put("needed_by", row.get("needed_by"));
            attributes.put("notes", row.get("notes"));
            attributes.put("created_at", row.get("created_at"));
            return Map.of(
                    "id", row.get("id").toString(),
                    "type", "InventoryRequisition",
                    "attributes", attributes
            );
        }).toList();

        return ResponseEntity.ok(response(data, requestId, correlationId));
    }

    @PostMapping("/requisitions")
    public ResponseEntity<Map<String, Object>> createRequisition(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody CreateRequisitionRequest request) {

        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String requisitionNumber = request.requisition_number() != null && !request.requisition_number().isBlank()
                ? request.requisition_number()
                : "REQ-" + now.toLocalDate().toString().replace("-", "") + "-" + id.toString().substring(0, 6).toUpperCase(Locale.ROOT);

        jdbcTemplate.update("""
                INSERT INTO inventory_requisitions
                    (id, tenant_id, facility_id, requisition_number, requested_by, item_count,
                     status, needed_by, notes, created_at, updated_at)
                VALUES (?, ?, ?::uuid, ?, ?, ?, 'SUBMITTED', ?::date, ?, ?, ?)
                """,
                id,
                tenantId,
                request.facility_id(),
                requisitionNumber,
                request.requested_by(),
                request.item_count() != null ? request.item_count() : 0,
                request.needed_by(),
                request.notes(),
                now,
                now
        );

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("requisition_number", requisitionNumber);
        attributes.put("requested_by", request.requested_by());
        attributes.put("item_count", request.item_count() != null ? request.item_count() : 0);
        attributes.put("status", "SUBMITTED");
        attributes.put("needed_by", request.needed_by() != null ? LocalDate.parse(request.needed_by()) : null);
        attributes.put("notes", request.notes());
        attributes.put("created_at", now);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", id.toString(),
                "type", "InventoryRequisition",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(InventoryItem i) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("facility_id", i.getFacilityId());
        attributes.put("product_code", i.getProductCode());
        attributes.put("product_name", i.getProductName());
        attributes.put("category", i.getCategory());
        attributes.put("quantity_on_hand", i.getQuantityOnHand());
        attributes.put("reorder_level", i.getReorderLevel());
        attributes.put("unit", i.getUnit());
        attributes.put("status", i.getStatus());
        attributes.put("last_counted_at", i.getLastCountedAt());
        attributes.put("created_at", i.getCreatedAt());
        attributes.put("updated_at", i.getUpdatedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", i.getId().toString());
        resource.put("type", "InventoryItem");
        resource.put("attributes", attributes);
        return resource;
    }

    private Map<String, Object> response(List<Map<String, Object>> data, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));
        return response;
    }
}
