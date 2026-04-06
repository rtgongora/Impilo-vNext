package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.domain.AuditLogEntry;
import zw.gov.mohcc.impilo.experience.repository.AuditLogRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Governance BFF Controller.
 *
 * Bridges the experience layer to existing runtime governance services:
 * - data-governance-service (datasets, rules, decisions, policy publish, export evaluation)
 * - experience-bff admin audit surface (for AI governance monitoring)
 *
 * Uses the shared serviceRestTemplate which auto-forwards v1.1 trust headers.
 */
@RestController
@RequestMapping("/internal/v1/ai-governance")
public class AiGovernanceController {

    private static final Logger log = LoggerFactory.getLogger(AiGovernanceController.class);

    private final RestTemplate restTemplate;
    private final String dataGovernanceUrl;
    private final AuditLogRepository auditLogRepository;

    public AiGovernanceController(
            RestTemplate serviceRestTemplate,
            ServiceClientConfig.ServiceEndpoints endpoints,
            AuditLogRepository auditLogRepository) {
        this.restTemplate = serviceRestTemplate;
        this.dataGovernanceUrl = endpoints.dataGovernanceBaseUrl();
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/datasets")
    public ResponseEntity<Map<String, Object>> listDatasets(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyGet(dataGovernanceUrl + "/internal/v1/governance/datasets", requestId, correlationId);
    }

    @PostMapping("/datasets")
    public ResponseEntity<Map<String, Object>> createDataset(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return proxyPost(dataGovernanceUrl + "/internal/v1/governance/datasets", body, requestId, correlationId, HttpStatus.CREATED);
    }

    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> listRules(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyGet(dataGovernanceUrl + "/internal/v1/governance/rules", requestId, correlationId);
    }

    @PostMapping("/rules")
    public ResponseEntity<Map<String, Object>> createRule(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return proxyPost(dataGovernanceUrl + "/internal/v1/governance/rules", body, requestId, correlationId, HttpStatus.CREATED);
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Map<String, Object>> deactivateRule(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode result = restTemplate.getForEntity(
                    dataGovernanceUrl + "/internal/v1/governance/rules/" + id, JsonNode.class).getBody();
            restTemplate.delete(dataGovernanceUrl + "/internal/v1/governance/rules/" + id);
            Object data = normalizePayload(result);
            return ResponseEntity.ok(metaEnvelope(data, requestId, correlationId));
        } catch (Exception e) {
            log.error("Deactivate governance rule failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", Map.of("code", "RULE_DEACTIVATE_FAILED", "message", e.getMessage())));
        }
    }

    @PostMapping("/decide")
    public ResponseEntity<Map<String, Object>> decide(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return proxyPost(dataGovernanceUrl + "/internal/v1/governance/decide", body, requestId, correlationId, HttpStatus.OK);
    }

    @PostMapping("/policies")
    public ResponseEntity<Map<String, Object>> publishPolicy(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return proxyPost(dataGovernanceUrl + "/internal/v1/governance/policies", body, requestId, correlationId, HttpStatus.CREATED);
    }

    @PostMapping("/exports/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateExport(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return proxyPost(dataGovernanceUrl + "/external/v1/exports", body, requestId, correlationId, HttpStatus.OK);
    }

    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> listAuditTrail(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("occurredAt").descending());
        Page<AuditLogEntry> result = auditLogRepository.findByTenantId(tenantId, pageable);
        List<Map<String, Object>> entries = result.getContent().stream().map(this::toAuditResource).toList();
        return ResponseEntity.ok(metaEnvelope(entries, requestId, correlationId));
    }

    // ── Proxy Helpers ───────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> proxyGet(String url, String requestId, String correlationId) {
        try {
            JsonNode result = restTemplate.getForEntity(url, JsonNode.class).getBody();
            Object data = normalizePayload(result);
            return ResponseEntity.ok(metaEnvelope(data, requestId, correlationId));
        } catch (Exception e) {
            log.warn("AI governance GET proxy failed for {}: {}", url, e.getMessage());
            return ResponseEntity.ok(metaEnvelope(new Object[0], requestId, correlationId));
        }
    }

    private ResponseEntity<Map<String, Object>> proxyPost(
            String url, Map<String, Object> body,
            String requestId, String correlationId, HttpStatus successCode) {
        try {
            JsonNode result = restTemplate.postForEntity(url, body, JsonNode.class).getBody();
            Object data = normalizePayload(result);
            return ResponseEntity.status(successCode).body(metaEnvelope(data, requestId, correlationId));
        } catch (Exception e) {
            log.error("AI governance POST proxy failed for {}: {}", url, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", Map.of("code", "AI_GOVERNANCE_OPERATION_FAILED", "message", e.getMessage())));
        }
    }

    private Object normalizePayload(JsonNode result) {
        if (result == null) return new Object[0];
        if (result.has("data")) return result.get("data");
        return result;
    }

    private Map<String, Object> metaEnvelope(Object data, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return response;
    }

    private Map<String, Object> toAuditResource(AuditLogEntry entry) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("actor_id", entry.getActorId());
        attributes.put("action", entry.getAction());
        attributes.put("resource_type", entry.getResourceType());
        attributes.put("resource_id", entry.getResourceId());
        attributes.put("details", entry.getDetails());
        attributes.put("ip_address", entry.getIpAddress());
        attributes.put("occurred_at", entry.getOccurredAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", entry.getId().toString());
        resource.put("type", "AuditLogEntry");
        resource.put("attributes", attributes);
        return resource;
    }
}
