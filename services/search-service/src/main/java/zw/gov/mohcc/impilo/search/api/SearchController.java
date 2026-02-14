package zw.gov.mohcc.impilo.search.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.search.api.dto.SearchRequest;
import zw.gov.mohcc.impilo.search.api.dto.SearchResponse;
import zw.gov.mohcc.impilo.search.service.SearchQueryService;

@RestController
@RequestMapping("/internal/v1/search")
public class SearchController {

    private final SearchQueryService searchQueryService;

    public SearchController(SearchQueryService searchQueryService) {
        this.searchQueryService = searchQueryService;
    }

    @PostMapping
    public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        SearchResponse response = searchQueryService.search(ctx.tenantId(), request);
        return ResponseEntity.ok(response);
    }
}
