package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.InventoryItem;
import zw.gov.mohcc.impilo.experience.repository.InventoryItemRepository;

import java.util.*;

/**
 * Inventory endpoints.
 * GET /internal/v1/inventory/items — list inventory items with facility_id filter, pagination.
 */
@RestController
@RequestMapping("/internal/v1/inventory")
public class InventoryController {

    private final InventoryItemRepository inventoryItemRepository;

    public InventoryController(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

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
}
