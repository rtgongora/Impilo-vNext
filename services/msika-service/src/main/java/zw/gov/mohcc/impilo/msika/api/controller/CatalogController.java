package zw.gov.mohcc.impilo.msika.api.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msika.api.dto.CatalogView;
import zw.gov.mohcc.impilo.msika.api.dto.CreateCatalogRequest;
import zw.gov.mohcc.impilo.msika.core.CatalogService;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.UUID;

@RestController
@RequestMapping("/v1/catalogs")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    @PostMapping
    public ResponseEntity<ApiResponse<CatalogView>> createCatalog(@Valid @RequestBody CreateCatalogRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        CatalogEntity catalog = catalogService.createCatalog(request);
        CatalogView view = catalogService.getCatalog(catalog.getCatalogId());
        return ResponseEntity.ok(ApiResponse.ok(view, correlationId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CatalogView>>> listCatalogs(
            @RequestParam(required = false) UUID tenantId,
            Pageable pageable) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        Page<CatalogView> page = catalogService.listCatalogs(tenantId, pageable);
        PagedResponse<CatalogView> paged = PagedResponse.of(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }

    @GetMapping("/{catalogId}")
    public ResponseEntity<ApiResponse<CatalogView>> getCatalog(@PathVariable String catalogId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        CatalogView view = catalogService.getCatalog(catalogId);
        return ResponseEntity.ok(ApiResponse.ok(view, correlationId));
    }

    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    @PostMapping("/{catalogId}/submit-review")
    public ResponseEntity<ApiResponse<CatalogView>> submitForReview(@PathVariable String catalogId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        catalogService.submitForReview(catalogId);
        CatalogView view = catalogService.getCatalog(catalogId);
        return ResponseEntity.ok(ApiResponse.ok(view, correlationId));
    }

    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    @PostMapping("/{catalogId}/approve")
    public ResponseEntity<ApiResponse<CatalogView>> approveCatalog(@PathVariable String catalogId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        catalogService.approveCatalog(catalogId);
        CatalogView view = catalogService.getCatalog(catalogId);
        return ResponseEntity.ok(ApiResponse.ok(view, correlationId));
    }

    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    @PostMapping("/{catalogId}/publish")
    public ResponseEntity<ApiResponse<CatalogView>> publishCatalog(@PathVariable String catalogId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        catalogService.publishCatalog(catalogId);
        CatalogView view = catalogService.getCatalog(catalogId);
        return ResponseEntity.ok(ApiResponse.ok(view, correlationId));
    }

    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    @PostMapping("/{catalogId}/rollback/{version}")
    public ResponseEntity<ApiResponse<CatalogView>> rollbackCatalog(
            @PathVariable String catalogId,
            @PathVariable String version) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        CatalogEntity rolled = catalogService.rollbackCatalog(catalogId, version);
        CatalogView view = catalogService.getCatalog(rolled.getCatalogId());
        return ResponseEntity.ok(ApiResponse.ok(view, correlationId));
    }
}
