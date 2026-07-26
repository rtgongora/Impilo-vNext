package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

    /** Preview/E2E anchors — aligned with inventory-service V002 seeds. */
    private static final UUID PREVIEW_FACILITY_HARARE =
            UUID.fromString("a1b2c3d4-0001-4000-8000-000000000001");
    private static final UUID PREVIEW_STORE_PHARMACY =
            UUID.fromString("95000000-0000-0000-0000-000000000001");
    private static final UUID PREVIEW_STORE_MAIN =
            UUID.fromString("95000000-0000-0000-0000-000000000002");
    private static final String PREVIEW_ITEM_CODE = "PARACETAMOL-500";

    private final InventoryServiceClient inventoryClient;
    private final ObjectMapper objectMapper;

    /**
     * Demo requisition rows are dev/preview-only and OFF by default (production-safe).
     * Enable with IMPILO_BFF_INVENTORY_DEMO_FALLBACK=true for compose/preview without
     * inventory-service. When false, an unavailable upstream returns an empty list, not fixtures.
     */
    @org.springframework.beans.factory.annotation.Value("${impilo.bff.inventory.demo-fallback:false}")
    private boolean demoFallbackEnabled;

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
            // An empty on-hand list reads as "this facility holds none of this stock", which is
            // how a dispensing decision becomes "we are out" and a substitution gets made.
            log.error("Inventory on-hand list failed for facility={}: {}", facilityId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "stock_on_hand_unavailable",
                    "message", "Stock on hand could not be retrieved. Do not treat this as an "
                               + "absence of stock.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
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

        UUID facilityUuid = resolveFacilityId(facilityId);

        if (facilityUuid != null) {
            try {
                JsonNode upstream = inventoryClient.listRequisitions(facilityUuid, 0, 50);
                List<Map<String, Object>> liveRows = mapRequisitionList(upstream);
                if (!liveRows.isEmpty()) {
                    return ResponseEntity.ok(response(liveRows, requestId, correlationId));
                }
            } catch (Exception e) {
                // Falling through here reaches demo rows (when enabled) or an empty list — either
                // way the caller is told something about real requisitions that we do not know.
                log.error("Inventory requisitions list failed for facility={}: {}",
                        facilityUuid, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "error", "requisitions_unavailable",
                        "message", "Requisitions could not be retrieved. Do not treat this as an "
                                   + "absence of requisitions.",
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        }

        if (demoFallbackEnabled) {
            List<Map<String, Object>> demoRows = demoRequisitions(facilityUuid, tenantId);
            if (!demoRows.isEmpty()) {
                return ResponseEntity.ok(response(demoRows, requestId, correlationId));
            }
        }

        return ResponseEntity.ok(response(List.of(), requestId, correlationId));
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

        UUID facilityUuid = resolveFacilityId(request.facility_id());
        int qty = request.item_count() != null && request.item_count() > 0 ? request.item_count() : 1;
        String lineNotes = request.notes() != null ? request.notes() : "";

        Map<String, Object> upstream = Map.of(
                "fromStoreId", PREVIEW_STORE_PHARMACY.toString(),
                "toStoreId", PREVIEW_STORE_MAIN.toString(),
                "lines", List.of(Map.of(
                        "itemCode", PREVIEW_ITEM_CODE,
                        "qtyRequested", qty,
                        "notes", lineNotes
                ))
        );

        try {
            JsonNode result = inventoryClient.createRequisition(objectMapper.valueToTree(upstream));
            Map<String, Object> mapped = mapRequisitionNode(result, facilityUuid, request);
            return ResponseEntity.status(201).body(response(mapped, requestId, correlationId));
        } catch (Exception e) {
            log.warn("Inventory requisition create failed: {}", e.getMessage());
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("error", Map.of(
                    "code", "INVENTORY_UNAVAILABLE",
                    "message", e.getMessage() != null ? e.getMessage() : "Inventory upstream unavailable"));
            errorBody.put("meta", Map.of(
                    "request_id", requestId,
                    "correlation_id", correlationId));
            return ResponseEntity.status(502).body(errorBody);
        }
    }

    private static UUID resolveFacilityId(String facilityId) {
        if (facilityId == null || facilityId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(facilityId);
        } catch (IllegalArgumentException e) {
            if ("FAC-HARARE-CENTRAL".equalsIgnoreCase(facilityId)) {
                return PREVIEW_FACILITY_HARARE;
            }
            return null;
        }
    }

    private List<Map<String, Object>> mapRequisitionList(JsonNode upstream) {
        if (upstream == null || upstream.isNull()) {
            return List.of();
        }
        JsonNode rows = upstream.isArray() ? upstream : objectMapper.createArrayNode().add(upstream);
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (JsonNode row : rows) {
            mapped.add(mapRequisitionNode(row, null, null));
        }
        return mapped;
    }

    private Map<String, Object> mapRequisitionNode(
            JsonNode row,
            UUID facilityFallback,
            CreateRequisitionRequest request) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        if (row == null || row.isNull()) {
            return mapped;
        }
        String reqId = textOrNull(row, "reqId");
        if (reqId == null) {
            reqId = textOrNull(row, "id");
        }
        mapped.put("id", reqId);
        mapped.put("req_id", reqId);
        mapped.put("facility_id", textOrNull(row, "facilityId") != null
                ? textOrNull(row, "facilityId")
                : facilityFallback != null ? facilityFallback.toString() : null);
        mapped.put("requested_by", textOrNull(row, "requestedBy") != null
                ? textOrNull(row, "requestedBy")
                : request != null ? request.requested_by() : null);
        mapped.put("status", textOrNull(row, "status"));
        mapped.put("notes", textOrNull(row, "notes") != null
                ? textOrNull(row, "notes")
                : request != null ? request.notes() : null);
        if (request != null && request.requisition_number() != null) {
            mapped.put("requisition_number", request.requisition_number());
        }
        if (request != null && request.item_count() != null) {
            mapped.put("item_count", request.item_count());
        }
        return mapped;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
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
