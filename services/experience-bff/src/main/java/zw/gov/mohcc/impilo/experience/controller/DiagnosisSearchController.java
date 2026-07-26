package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.SearchServiceClient;
import zw.gov.mohcc.impilo.experience.support.Icd11SearchNormalizer;

import java.util.List;
import java.util.Map;

/**
 * Shared ICD-11 search for web and mobile experience layers.
 * GET /internal/v1/diagnosis/icd11/search?q=
 */
@RestController
@RequestMapping("/internal/v1/diagnosis")
public class DiagnosisSearchController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisSearchController.class);

    private final SearchServiceClient searchClient;

    public DiagnosisSearchController(SearchServiceClient searchClient) {
        this.searchClient = searchClient;
    }

    @GetMapping("/icd11/search")
    public ResponseEntity<Map<String, Object>> searchIcd11(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            JsonNode result = searchClient.search(query, "ICD11", 0, limit);
            List<Map<String, Object>> normalized = Icd11SearchNormalizer.normalize(result);
            return ResponseEntity.ok(Map.of(
                    "data", normalized,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        } catch (Exception ex) {
            // No results reads as "there is no ICD-11 code for this", which pushes the clinician
            // to free-text a diagnosis that does have a code — a coding gap created by an outage.
            log.error("ICD-11 search failed for query={}: {}", query, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "diagnosis_search_unavailable",
                    "message", "ICD-11 could not be searched. Do not treat this as an absence of "
                               + "matching codes.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        }
    }
}
