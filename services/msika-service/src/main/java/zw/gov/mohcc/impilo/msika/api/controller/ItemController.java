package zw.gov.mohcc.impilo.msika.api.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msika.api.dto.*;
import zw.gov.mohcc.impilo.msika.core.ItemService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

@RestController
@RequestMapping("/v1")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    @PostMapping("/catalogs/{catalogId}/items")
    public ResponseEntity<ApiResponse<CatalogItemView>> createItem(
            @PathVariable String catalogId,
            @Valid @RequestBody CreateItemRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        CatalogItemView view = itemService.createItem(catalogId, request);
        return ResponseEntity.ok(ApiResponse.ok(view, correlationId));
    }

    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CatalogItemView>> updateItem(
            @PathVariable String itemId,
            @Valid @RequestBody UpdateItemRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        CatalogItemView view = itemService.updateItem(itemId, request);
        return ResponseEntity.ok(ApiResponse.ok(view, correlationId));
    }

    @GetMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CatalogItemView>> getItem(@PathVariable String itemId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        CatalogItemView view = itemService.getItem(itemId);
        return ResponseEntity.ok(ApiResponse.ok(view, correlationId));
    }

    @GetMapping("/catalogs/{catalogId}/items")
    public ResponseEntity<ApiResponse<PagedResponse<CatalogItemView>>> listItems(
            @PathVariable String catalogId,
            @RequestParam(required = false) String kind,
            Pageable pageable) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        Page<CatalogItemView> page = itemService.listItems(catalogId, kind, pageable);
        PagedResponse<CatalogItemView> paged = PagedResponse.of(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }
}
