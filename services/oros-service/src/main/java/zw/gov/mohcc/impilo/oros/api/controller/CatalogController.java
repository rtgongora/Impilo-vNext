package zw.gov.mohcc.impilo.oros.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.CatalogImportRequest;
import zw.gov.mohcc.impilo.oros.api.dto.CatalogItemDto;
import zw.gov.mohcc.impilo.oros.core.CatalogService;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.CatalogItemEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for orderable catalog management.
 *
 * <p>Provides endpoints for browsing the catalog, looking up items
 * by code, and importing catalog items in bulk.</p>
 */
@RestController
@RequestMapping("/v1/catalog")
public class CatalogController {

    private static final Logger log = LoggerFactory.getLogger(CatalogController.class);

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * Get the catalog, optionally filtered by order type.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CatalogItemResponse>>> getCatalog(
            @RequestParam(required = false) OrderType orderType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        Page<CatalogItemEntity> result = catalogService.getCatalog(orderType, PageRequest.of(page, size));

        List<CatalogItemResponse> items = result.getContent().stream()
                .map(CatalogItemResponse::from)
                .collect(Collectors.toList());

        PagedResponse<CatalogItemResponse> pagedResponse = PagedResponse.of(
                items, page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(pagedResponse, correlationId));
    }

    /**
     * Import catalog items in bulk.
     */
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<List<CatalogItemResponse>>> importCatalog(
            @Valid @RequestBody CatalogImportRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<CatalogService.CatalogItemImport> imports = request.items().stream()
                .map(dto -> new CatalogService.CatalogItemImport(
                        dto.orderType(), dto.code(), dto.displayName(),
                        dto.category(), dto.ziboCanonical()))
                .collect(Collectors.toList());

        List<CatalogItemResponse> imported = catalogService.importCatalog(imports).stream()
                .map(CatalogItemResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(imported, correlationId));
    }

    /**
     * Look up a single catalog item by code.
     */
    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<CatalogItemResponse>> lookupItem(@PathVariable String code) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        CatalogItemEntity item = catalogService.lookupItem(code);
        return ResponseEntity.ok(ApiResponse.ok(CatalogItemResponse.from(item), correlationId));
    }

    /**
     * Response DTO for catalog item data.
     */
    record CatalogItemResponse(String catalogId, OrderType orderType, String code,
                               String displayName, String category,
                               String ziboCanonical, boolean active) {
        static CatalogItemResponse from(CatalogItemEntity entity) {
            return new CatalogItemResponse(
                    entity.getCatalogId().toString(),
                    entity.getOrderType(),
                    entity.getCode(),
                    entity.getDisplayName(),
                    entity.getCategory(),
                    entity.getZiboCanonical(),
                    entity.isActive());
        }
    }
}
