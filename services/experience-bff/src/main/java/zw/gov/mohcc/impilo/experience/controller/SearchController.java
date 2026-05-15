package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.LearningServiceClient;
import zw.gov.mohcc.impilo.experience.client.SearchServiceClient;

import java.util.Map;

/**
 * BFF proxy to search-service federated index ({@link SearchServiceClient}).
 * UI entry: {@code GET /internal/v1/search?q=&entityType=&page=&size=} (entityType optional; matches search-service).
 */
@RestController
@RequestMapping("/internal/v1/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchServiceClient searchClient;
    private final LearningServiceClient learningClient;
    private final ObjectMapper objectMapper;

    public SearchController(
            SearchServiceClient searchClient, LearningServiceClient learningClient, ObjectMapper objectMapper) {
        this.searchClient = searchClient;
        this.learningClient = learningClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> search(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam String q,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String rankMode) {
        try {
            JsonNode data = searchClient.search(q, entityType, page, size, rankMode);
            JsonNode merged = mergeLearningHits(data, q, entityType, page, size);
            return ResponseEntity.ok(Map.of(
                    "data", merged != null ? merged : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Search proxy failed: {}", e.getMessage());
            return upstreamFailure("SEARCH_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private JsonNode mergeLearningHits(JsonNode platformSearch, String q, String entityType, int page, int size) {
        if (platformSearch == null || !platformSearch.isObject()) {
            return platformSearch;
        }
        if (entityType != null && !entityType.isBlank() && !"learning_resource".equalsIgnoreCase(entityType)) {
            return platformSearch;
        }
        if (!learningClient.isConfigured() || q == null || q.trim().length() < 2) {
            return platformSearch;
        }
        JsonNode learningEnvelope = learningClient.searchHits(q.trim(), Math.min(8, Math.max(1, size)));
        if (learningEnvelope == null || !learningEnvelope.isObject() || !learningEnvelope.has("results")) {
            return platformSearch;
        }
        ArrayNode learningResults = (ArrayNode) learningEnvelope.get("results");
        if (learningResults.isEmpty()) {
            return platformSearch;
        }
        ObjectNode copy = platformSearch.deepCopy();
        ArrayNode combined = objectMapper.createArrayNode();
        if (copy.has("results") && copy.get("results").isArray()) {
            for (Iterator<JsonNode> it = copy.get("results").elements(); it.hasNext(); ) {
                combined.add(it.next());
            }
        }
        for (JsonNode hit : learningResults) {
            combined.add(hit);
        }
        copy.set("results", combined);
        if (copy.has("totalElements")) {
            copy.put("totalElements", combined.size());
        }
        return copy;
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<Map<String, Object>> getDocument(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String documentId) {
        try {
            JsonNode data = searchClient.getDocument(documentId);
            if (data == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", Map.of("code", "DOCUMENT_NOT_FOUND", "message", "Document not found"),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Search document fetch failed: {}", e.getMessage());
            return upstreamFailure("SEARCH_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Search upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
