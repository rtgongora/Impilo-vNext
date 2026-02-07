package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.BarcodeResultDto;
import zw.gov.mohcc.impilo.inventory.api.dto.CreateItemRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.ItemDto;
import zw.gov.mohcc.impilo.inventory.api.dto.ItemImportRequest;
import zw.gov.mohcc.impilo.inventory.core.BarcodeLookupService;
import zw.gov.mohcc.impilo.inventory.core.ItemService;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for inventory item catalog management.
 *
 * <p>Provides endpoints for creating, retrieving, importing, and looking up
 * items by barcode. All operations are tenant-scoped via the trust context.</p>
 */
@RestController
@RequestMapping("/v1/items")
public class ItemController {

    private static final Logger log = LoggerFactory.getLogger(ItemController.class);

    private final ItemService itemService;
    private final BarcodeLookupService barcodeLookupService;

    public ItemController(ItemService itemService, BarcodeLookupService barcodeLookupService) {
        this.itemService = itemService;
        this.barcodeLookupService = barcodeLookupService;
    }

    /**
     * Create a new item in the inventory catalog.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ItemDto>> createItem(@Valid @RequestBody CreateItemRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ItemEntity entity = itemService.createItem(
                request.itemCode(), request.name(), request.uom(),
                request.barcode(), request.category(), request.controlled(),
                request.ziboRefsJson());

        log.info("Item created: itemCode={}", entity.getItemCode());

        return ResponseEntity.ok(ApiResponse.ok(ItemDto.from(entity), correlationId));
    }

    /**
     * Bulk import items into the catalog (JSON list).
     */
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<List<ItemDto>>> importItems(
            @Valid @RequestBody ItemImportRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<ItemService.ItemImportData> importData = request.items().stream()
                .map(item -> new ItemService.ItemImportData(
                        item.itemCode(), item.name(), item.uom(),
                        item.barcode(), item.category(), item.controlled()))
                .collect(Collectors.toList());

        List<ItemEntity> entities = itemService.importItems(importData);

        log.info("Items imported: count={}", entities.size());

        List<ItemDto> dtos = entities.stream()
                .map(ItemDto::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    /**
     * Look up an item by its barcode (GTIN-14, GTIN-13, or internal format).
     */
    @GetMapping("/lookup-by-barcode")
    public ResponseEntity<ApiResponse<BarcodeResultDto>> lookupByBarcode(
            @RequestParam("code") String barcode) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        BarcodeLookupService.BarcodeResult result = barcodeLookupService.lookupByBarcode(barcode);

        return ResponseEntity.ok(ApiResponse.ok(BarcodeResultDto.from(result), correlationId));
    }

    /**
     * Get a single item by its item code.
     */
    @GetMapping("/{itemCode}")
    public ResponseEntity<ApiResponse<ItemDto>> getItem(@PathVariable String itemCode) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        ItemEntity entity = itemService.getItem(itemCode);

        return ResponseEntity.ok(ApiResponse.ok(ItemDto.from(entity), correlationId));
    }
}
