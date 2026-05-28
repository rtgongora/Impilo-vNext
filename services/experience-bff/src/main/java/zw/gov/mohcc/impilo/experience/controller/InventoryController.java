package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.InventoryServiceClient;

import java.util.*;

/**
 * Inventory endpoints. Delegates to InventoryServiceClient.
 * GET /internal/v1/inventory/items — list inventory items with facility_id filter, pagination.
 */
@RestController
@RequestMapping("/internal/v1/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryServiceClient inventoryClient;
    private final ObjectMapper objectMapper;

    public InventoryController(InventoryServiceClient inventoryClient, ObjectMapper objectMapper) {
        this.inventoryClient = inventoryClient;
        this.objectMapper = objectMapper;
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

        int limit = Math.min(size, 100);

        UUID facilityUuid = null;
        if (facilityId != null && !facilityId.isBlank()) {
            try {
                facilityUuid = UUID.fromString(facilityId);
            } catch (IllegalArgumentException e) {
                facilityUuid = null;
            }
        }

        try {
            JsonNode result = inventoryClient.getOnHand(facilityUuid, null, null, null, page, limit);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", result != null ? result : List.of());
            response.put("meta", Map.of(
                    "request_id", requestId,
                    "correlation_id", correlationId
            ));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Inventory on-hand list unavailable: {}", e.getMessage());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", List.of());
            response.put("meta", Map.of(
                    "request_id", requestId,
                    "correlation_id", correlationId
            ));
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/counts")
    public ResponseEntity<Map<String, Object>> listCounts(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "facility_id") String facilityId) {

        JsonNode result = inventoryClient.getReconcilePending(0, 20);

        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, ...);

        return ResponseEntity.ok(response(result, requestId, correlationId));
    }

    @GetMapping("/movements")
    public ResponseEntity<Map<String, Object>> listMovements(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "facility_id") String facilityId) {

        UUID facilityUuid = facilityId != null ? UUID.fromString(facilityId) : null;
        JsonNode result = inventoryClient.getLedger(facilityUuid, null, null, 0, 50);

        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, ...);

        return ResponseEntity.ok(response(result, requestId, correlationId));
    }

    @GetMapping("/requisitions")
    public ResponseEntity<Map<String, Object>> listRequisitions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false, name = "facility_id") String facilityId) {

        UUID facilityUuid = null;
        if (facilityId != null && !facilityId.isBlank()) {
            try {
                facilityUuid = UUID.fromString(facilityId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid facility_id for requisitions list: {}", facilityId);
            }
        }

        List<Map<String, Object>> demoRows = demoRequisitions(facilityUuid, tenantId);
        if (!demoRows.isEmpty()) {
            return ResponseEntity.ok(response(demoRows, requestId, correlationId));
        }

        try {
            JsonNode result = inventoryClient.getReconcilePending(0, 50);
            return ResponseEntity.ok(response(result, requestId, correlationId));
        } catch (Exception e) {
            log.warn("Inventory requisitions list unavailable: {}", e.getMessage());
            return ResponseEntity.ok(response(List.of(), requestId, correlationId));
        }
    }

    /** Demo rows aligned with Flyway V31 seeds until inventory-service is in compose. */
    private static List<Map<String, Object>> demoRequisitions(UUID facilityId, String tenantId) {
        if (facilityId == null) {
            return List.of();
        }
        String facility = facilityId.toString();
        if ("a1b2c3d4-0001-4000-8000-000000000001".equals(facility)) {
            return List.of(
                    demoRequisition(
                            "94000000-0000-0000-0000-000000000001",
                            "tenant-moh-zw",
                            facility,
                            "REQ-HCH-20260408-01",
                            "Harare Central Pharmacy",
                            6,
                            "SUBMITTED"),
                    demoRequisition(
                            "94000000-0000-0000-0000-000000000002",
                            "tenant-moh-zw",
                            facility,
                            "REQ-HCH-20260407-03",
                            "Emergency Unit",
                            3,
                            "APPROVED"));
        }
        if ("f1000000-0000-0000-0000-000000000001".equals(facility)) {
            return List.of(demoRequisition(
                    "94000000-0000-0000-0000-000000000003",
                    "moh-zw",
                    facility,
                    "REQ-SMC-20260406-02",
                    "Medical Stores",
                    4,
                    "FULFILLED"));
        }
        return List.of();
    }

    private static Map<String, Object> demoRequisition(
            String id,
            String tenant,
            String facility,
            String number,
            String requestedBy,
            int itemCount,
            String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("tenant_id", tenant);
        row.put("facility_id", facility);
        row.put("requisition_number", number);
        row.put("requested_by", requestedBy);
        row.put("item_count", itemCount);
        row.put("status", status);
        return row;
    }

    @PostMapping("/requisitions")
    public ResponseEntity<Map<String, Object>> createRequisition(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody CreateRequisitionRequest request) {

        JsonNode body = objectMapper.valueToTree(Map.of(
                "facilityId", request.facility_id() != null ? request.facility_id() : "",
                "requisitionNumber", request.requisition_number() != null ? request.requisition_number() : "",
                "requestedBy", request.requested_by() != null ? request.requested_by() : "",
                "itemCount", request.item_count() != null ? request.item_count() : 0,
                "neededBy", request.needed_by() != null ? request.needed_by() : "",
                "notes", request.notes() != null ? request.notes() : ""
        ));

        JsonNode result = inventoryClient.createRequisition(body);

        // jdbcTemplate.update("""
        //     INSERT INTO inventory_requisitions (...) VALUES (...)
        //     """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> response(Object data, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));
        return response;
    }
}
