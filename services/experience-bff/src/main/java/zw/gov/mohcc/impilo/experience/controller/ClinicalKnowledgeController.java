package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BFF for national clinical knowledge (EDLIZ-aligned). Proxies to clinical-knowledge-platform-service.
 */
@RestController
@RequestMapping("/internal/v1/clinical")
public class ClinicalKnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalKnowledgeController.class);

    private final ClinicalKnowledgePlatformClient clinicalClient;

    public ClinicalKnowledgeController(ClinicalKnowledgePlatformClient clinicalClient) {
        this.clinicalClient = clinicalClient;
    }

    @PostMapping("/assistant/ask")
    public ResponseEntity<Map<String, Object>> askEdliz(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = clinicalClient.assistantAsk(body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            log.error("Clinical assistant ask failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", fallbackUnsupported()));
        }
    }

    @GetMapping("/assistant/traces/{id}")
    public ResponseEntity<Map<String, Object>> trace(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        try {
            JsonNode data = clinicalClient.getTrace(id);
            if (data == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of("data", data));
        } catch (Exception e) {
            log.error("Clinical trace fetch failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/prescribing/evaluate")
    public ResponseEntity<Map<String, Object>> prescribing(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = clinicalClient.prescribingEvaluate(body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            log.error("Clinical prescribing evaluate failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", Map.of("alerts", java.util.List.of())));
        }
    }

    @GetMapping("/pathways")
    public ResponseEntity<Map<String, Object>> pathways(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        try {
            JsonNode data = clinicalClient.listPathways();
            return ResponseEntity.ok(Map.of("data", data != null ? data : java.util.List.of()));
        } catch (Exception e) {
            log.error("Clinical pathways list failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", java.util.List.of()));
        }
    }

    @PostMapping("/pathways/sessions")
    public ResponseEntity<Map<String, Object>> startPathwaySession(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = clinicalClient.startPathwaySession(body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            log.error("Clinical pathway session start failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", Map.of("error", "unavailable")));
        }
    }

    @PostMapping("/pathways/sessions/{sessionId}/advance")
    public ResponseEntity<Map<String, Object>> advancePathwaySession(
            @PathVariable UUID sessionId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = clinicalClient.advancePathwaySession(sessionId, body);
            return ResponseEntity.ok(Map.of("data", data != null ? data : Map.of()));
        } catch (Exception e) {
            log.error("Clinical pathway advance failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", Map.of("error", "unavailable")));
        }
    }

    private static Map<String, Object> fallbackUnsupported() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("answer_summary", "The clinical knowledge service is temporarily unavailable. No automated recommendation was generated.");
        m.put("support_mode", "INSUFFICIENT_EVIDENCE");
        m.put("source_citations", java.util.List.of());
        m.put("warnings", java.util.List.of());
        return m;
    }
}
