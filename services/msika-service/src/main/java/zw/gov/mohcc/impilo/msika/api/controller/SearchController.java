package zw.gov.mohcc.impilo.msika.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msika.api.dto.CatalogItemView;
import zw.gov.mohcc.impilo.msika.core.SearchService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

@RestController
@RequestMapping("/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CatalogItemView>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String restriction,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String ziboCode,
            @RequestParam(required = false) String tenantId,
            Pageable pageable) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        Page<CatalogItemView> page = searchService.search(q, kind, restriction, tag, ziboCode, tenantId, pageable);
        PagedResponse<CatalogItemView> paged = PagedResponse.of(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }
}
