package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.CommodityCategoryDto;
import zw.gov.mohcc.impilo.inventory.api.dto.CommodityDto;
import zw.gov.mohcc.impilo.inventory.api.dto.CreateCommodityCategoryRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.UpdateCommodityAttributesRequest;
import zw.gov.mohcc.impilo.inventory.core.CommodityCatalogueService;
import zw.gov.mohcc.impilo.inventory.core.ItemService;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CommodityCategoryEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dura commodity catalogue REST API.
 *
 * <p>Serves the Dura namespace ({@code /v1/dura/*}; {@code /api/v1/dura/*} via the
 * gateway) for commodity categories and the enriched commodity catalogue. All
 * operations are tenant-scoped via the trust context.</p>
 */
@RestController
@RequestMapping("/v1/dura")
public class DuraCommodityController {

    private static final Logger log = LoggerFactory.getLogger(DuraCommodityController.class);

    private final CommodityCatalogueService catalogueService;
    private final ItemService itemService;

    public DuraCommodityController(CommodityCatalogueService catalogueService, ItemService itemService) {
        this.catalogueService = catalogueService;
        this.itemService = itemService;
    }

    // ── Categories ──────────────────────────────────────────────

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<CommodityCategoryDto>> createCategory(
            @Valid @RequestBody CreateCommodityCategoryRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        CommodityCategoryEntity category = catalogueService.createCategory(
                request.code(), request.name(), request.parentCategoryId(),
                request.programmeArea(), request.description());
        log.info("Dura category created: code={}", category.getCode());
        return ResponseEntity.ok(ApiResponse.ok(CommodityCategoryDto.from(category), correlationId));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CommodityCategoryDto>>> listCategories(
            @RequestParam(value = "programmeArea", required = false) String programmeArea) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<CommodityCategoryDto> dtos = catalogueService.listCategories(programmeArea).stream()
                .map(CommodityCategoryDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @GetMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<CommodityCategoryDto>> getCategory(@PathVariable UUID categoryId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        CommodityCategoryEntity category = catalogueService.getCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.ok(CommodityCategoryDto.from(category), correlationId));
    }

    // ── Commodities ─────────────────────────────────────────────

    @GetMapping("/commodities")
    public ResponseEntity<ApiResponse<List<CommodityDto>>> searchCommodities(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "programmeArea", required = false) String programmeArea,
            @RequestParam(value = "controlled", required = false) Boolean controlled,
            @RequestParam(value = "coldChain", required = false) Boolean coldChain) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<CommodityDto> dtos = catalogueService
                .searchCommodities(q, categoryId, programmeArea, controlled, coldChain).stream()
                .map(CommodityDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    @GetMapping("/commodities/{itemCode}")
    public ResponseEntity<ApiResponse<CommodityDto>> getCommodity(@PathVariable String itemCode) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ItemEntity item = itemService.getItem(itemCode);
        return ResponseEntity.ok(ApiResponse.ok(CommodityDto.from(item), correlationId));
    }

    @PatchMapping("/commodities/{itemCode}/attributes")
    public ResponseEntity<ApiResponse<CommodityDto>> updateAttributes(
            @PathVariable String itemCode,
            @Valid @RequestBody UpdateCommodityAttributesRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ItemEntity item = catalogueService.updateCommodityAttributes(itemCode, request.toAttributes());
        log.info("Dura commodity attributes updated: itemCode={}", itemCode);
        return ResponseEntity.ok(ApiResponse.ok(CommodityDto.from(item), correlationId));
    }
}
