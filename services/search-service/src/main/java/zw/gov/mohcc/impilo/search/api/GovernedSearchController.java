package zw.gov.mohcc.impilo.search.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.search.api.dto.SearchRequest;
import zw.gov.mohcc.impilo.search.api.dto.SearchResponse;
import zw.gov.mohcc.impilo.search.service.SearchQueryService;

import java.util.List;

/**
 * Governed search surfaces: clinical-safe (restricted fields) and global (wider fields).
 */
@RestController
public class GovernedSearchController {

    private static final List<String> CLINICAL_SAFE_FIELDS = List.of("title", "resourceType", "status", "category");

    private final SearchQueryService searchQueryService;

    public GovernedSearchController(SearchQueryService searchQueryService) {
        this.searchQueryService = searchQueryService;
    }

    /**
     * Clinical-safe search: restricts returned fields to non-PII subset.
     * Does NOT return bodyText, metadata, or any patient-identifying information.
     */
    @PostMapping("/internal/v1/search/clinical-safe")
    public ResponseEntity<SearchResponse> clinicalSafeSearch(@Valid @RequestBody SearchRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        SearchResponse fullResponse = searchQueryService.search(ctx.tenantId(), request);

        // Strip sensitive fields from hits — only return title + snippet (no metadata)
        List<SearchResponse.SearchHit> safeHits = fullResponse.hits().stream()
                .map(hit -> new SearchResponse.SearchHit(
                        hit.id(),
                        hit.indexId(),
                        hit.externalId(),
                        hit.title(),
                        hit.snippet(),
                        hit.score(),
                        null  // metadata stripped for clinical-safe
                ))
                .toList();

        return ResponseEntity.ok(new SearchResponse(safeHits, fullResponse.totalHits(),
                fullResponse.page(), fullResponse.size()));
    }

    /**
     * Global search: returns all fields including metadata.
     * Requires stricter authorization (validated upstream by TSHEPO ext_authz).
     */
    @PostMapping("/internal/v1/search/global")
    public ResponseEntity<SearchResponse> globalSearch(@Valid @RequestBody SearchRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        SearchResponse response = searchQueryService.search(ctx.tenantId(), request);
        return ResponseEntity.ok(response);
    }
}
