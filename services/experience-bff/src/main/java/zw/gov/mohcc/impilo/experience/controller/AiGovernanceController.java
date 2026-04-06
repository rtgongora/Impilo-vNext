package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * AI Governance BFF Controller — bridges the experience UI to the
 * data-governance-service sovereign API surface.
 *
 * <p>Proxied operations:
 * <ul>
 *   <li>Datasets: create + list</li>
 *   <li>Rules: create + list + deactivate</li>
 *   <li>Decisions: evaluate access</li>
 *   <li>Policies: publish</li>
 *   <li>Audit: local admin_audit_log query</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/ai-governance")
public class AiGovernanceController {

    private static final Logger log = LoggerFactory.getLogger(AiGovernanceController.class);

    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbc;
    private final String governanceBaseUrl;

    public AiGovernanceController(
            JdbcTemplate jdbc,
            @Value("${impilo.services.data-governance-base-url:http://localhost:8220}") String governanceBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.jdbc = jdbc;
        this.governanceBaseUrl = governanceBaseUrl;
    }

    // ── Datasets ────────────────────────────────────────────────────

    @GetMapping("/datasets")
    public ResponseEntity<String> listDatasets(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return proxyGet("/internal/v1/governance/datasets", tenantId, requestId, correlationId);
    }

    @PostMapping("/datasets")
    public ResponseEntity<String> createDataset(
            @RequestBody String body,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return proxyPost("/internal/v1/governance/datasets", body, tenantId, requestId, correlationId);
    }

    // ── Rules ───────────────────────────────────────────────────────

    @GetMapping("/rules")
    public ResponseEntity<String> listRules(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return proxyGet("/internal/v1/governance/rules", tenantId, requestId, correlationId);
    }

    @PostMapping("/rules")
    public ResponseEntity<String> createRule(
            @RequestBody String body,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return proxyPost("/internal/v1/governance/rules", body, tenantId, requestId, correlationId);
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<String> deactivateRule(
            @PathVariable String ruleId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return proxyDelete("/internal/v1/governance/rules/" + ruleId, tenantId, requestId, correlationId);
    }

    // ── Decisions ───────────────────────────────────────────────────

    @PostMapping("/decide")
    public ResponseEntity<String> decide(
            @RequestBody String body,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return proxyPost("/internal/v1/governance/decide", body, tenantId, requestId, correlationId);
    }

    // ── Policies ────────────────────────────────────────────────────

    @PostMapping("/policies")
    public ResponseEntity<String> publishPolicy(
            @RequestBody String body,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId) {
        return proxyPost("/internal/v1/governance/policies", body, tenantId, requestId, correlationId);
    }

    // ── Audit (local BFF table) ─────────────────────────────────────

    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> listAuditEntries(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(defaultValue = "50") int size) {
        int limit = Math.min(size, 200);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM admin_audit_log WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ?",
                tenantId, limit);
        return ResponseEntity.ok(Map.of("data", rows));
    }

    // ── Proxy Helpers ───────────────────────────────────────────────

    private HttpHeaders buildHeaders(String tenantId, String requestId, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Tenant-ID", tenantId);
        headers.set("X-Pod-ID", "national-spine");
        headers.set("X-Request-ID", requestId);
        headers.set("X-Correlation-ID", correlationId);
        return headers;
    }

    private ResponseEntity<String> proxyGet(String path, String tenantId, String requestId, String correlationId) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    governanceBaseUrl + path, HttpMethod.GET,
                    new HttpEntity<>(buildHeaders(tenantId, requestId, correlationId)),
                    String.class);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("AI Governance proxy GET {} failed: {}", path, e.getMessage());
            return ResponseEntity.status(502).body("{\"error\":\"Governance service unavailable\"}");
        }
    }

    private ResponseEntity<String> proxyPost(String path, String body, String tenantId, String requestId, String correlationId) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    governanceBaseUrl + path, HttpMethod.POST,
                    new HttpEntity<>(body, buildHeaders(tenantId, requestId, correlationId)),
                    String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("AI Governance proxy POST {} failed: {}", path, e.getMessage());
            return ResponseEntity.status(502).body("{\"error\":\"Governance service unavailable\"}");
        }
    }

    private ResponseEntity<String> proxyDelete(String path, String tenantId, String requestId, String correlationId) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    governanceBaseUrl + path, HttpMethod.DELETE,
                    new HttpEntity<>(buildHeaders(tenantId, requestId, correlationId)),
                    String.class);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("AI Governance proxy DELETE {} failed: {}", path, e.getMessage());
            return ResponseEntity.status(502).body("{\"error\":\"Governance service unavailable\"}");
        }
    }
}
