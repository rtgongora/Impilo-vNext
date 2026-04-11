package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.SearchServiceClient;

import java.util.Map;

/**
 * BFF proxy to search-service federated index ({@link SearchServiceClient}).
 * UI entry: {@code GET /internal/v1/search?q=&domain=&page=&size=}.
 */
@RestController
@RequestMapping("/internal/v1/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchServiceClient searchClient;

    public SearchController(SearchServiceClient searchClient) {
        this.searchClient = searchClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> search(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String q,
            @RequestParam(defaultValue = "ALL") String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode data = searchClient.search(q, domain, page, size);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            log.error("Search proxy failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", Map.of()));
        }
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<Map<String, Object>> getDocument(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String documentId) {
        try {
            JsonNode data = searchClient.getDocument(documentId);
            if (data == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of("data", data));
        } catch (Exception e) {
            log.error("Search document fetch failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
