package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BFF proxy for clinical knowledge curation (review queue, approve/reject).
 */
@RestController
@RequestMapping("/internal/v1/clinical/curation")
public class ClinicalCurationController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalCurationController.class);

    private final ClinicalKnowledgePlatformClient clinicalClient;

    public ClinicalCurationController(ClinicalKnowledgePlatformClient clinicalClient) {
        this.clinicalClient = clinicalClient;
    }

    @GetMapping("/review-items")
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(name = "status", defaultValue = "PROPOSED") String status) {
        try {
            JsonNode data = clinicalClient.listKnowledgeReviewItems(status);
            return ResponseEntity.ok(Map.of("data", data != null ? data : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode()));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(errorBody(e));
        } catch (Exception e) {
            log.error("Curation list failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "clinical_platform_unreachable", "detail", String.valueOf(e.getMessage())));
        }
    }

    @PostMapping("/review-items/{id}/decision")
    public ResponseEntity<Map<String, Object>> decide(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = clinicalClient.decideKnowledgeReviewItem(id, body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(errorBody(e));
        } catch (Exception e) {
            log.error("Curation decision failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "clinical_platform_unreachable", "detail", String.valueOf(e.getMessage())));
        }
    }

    private static Map<String, Object> errorBody(HttpStatusCodeException e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", e.getStatusCode().value());
        m.put("detail", e.getResponseBodyAsString());
        return m;
    }
}
