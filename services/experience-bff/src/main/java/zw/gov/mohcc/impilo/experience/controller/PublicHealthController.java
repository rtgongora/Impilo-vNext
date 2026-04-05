package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * Public Health BFF Controller — bridges experience UI to
 * surveillance-service, campaigns-service, and indawo-service.
 */
@RestController
@RequestMapping("/internal/v1/public-health")
public class PublicHealthController {

    private static final Logger log = LoggerFactory.getLogger(PublicHealthController.class);
    private final RestTemplate restTemplate;
    private final String surveillanceUrl;
    private final String campaignsUrl;
    private final String indawoUrl;

    public PublicHealthController(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.surveillanceUrl = endpoints.surveillanceBaseUrl();
        this.campaignsUrl = endpoints.campaignsBaseUrl();
        this.indawoUrl = endpoints.indawoBaseUrl();
    }

    // ── Surveillance ─────────────────────────────────────────────

    @GetMapping("/signals")
    public ResponseEntity<Map<String, Object>> listSignals(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return proxy(surveillanceUrl + "/internal/v1/signals", requestId);
    }

    @GetMapping("/cases")
    public ResponseEntity<Map<String, Object>> listCases(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return proxy(surveillanceUrl + "/internal/v1/cases", requestId);
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> listAlerts(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return proxy(surveillanceUrl + "/internal/v1/surveillance/alerts", requestId);
    }

    @GetMapping("/counters")
    public ResponseEntity<Map<String, Object>> getCounters(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return proxy(surveillanceUrl + "/internal/v1/surveillance/counters", requestId);
    }

    // ── Campaigns ────────────────────────────────────────────────

    @GetMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> listCampaigns(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return proxy(campaignsUrl + "/internal/v1/campaigns", requestId);
    }

    @PostMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> createCampaign(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode result = restTemplate.postForEntity(campaignsUrl + "/internal/v1/campaigns", body, JsonNode.class).getBody();
            return ResponseEntity.status(201).body(Map.of("data", result != null ? result : Map.of(), "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            log.error("Campaign creation failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "FAILED", "message", e.getMessage())));
        }
    }

    @PostMapping("/campaigns/{id}/dispatch")
    public ResponseEntity<Map<String, Object>> dispatchCampaign(
            @PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        try {
            JsonNode result = restTemplate.postForEntity(campaignsUrl + "/internal/v1/campaigns/" + id + "/dispatch", Map.of(), JsonNode.class).getBody();
            return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of(), "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "DISPATCH_FAILED", "message", e.getMessage())));
        }
    }

    // ── Indawo Sites ─────────────────────────────────────────────

    @GetMapping("/sites")
    public ResponseEntity<Map<String, Object>> listSites(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return proxy(indawoUrl + "/internal/v1/sites", requestId);
    }

    @GetMapping("/sites/{id}")
    public ResponseEntity<Map<String, Object>> getSite(@PathVariable String id, @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        return proxy(indawoUrl + "/internal/v1/sites/" + id, requestId);
    }

    // ── Helper ───────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> proxy(String url, String requestId) {
        try {
            JsonNode result = restTemplate.getForEntity(url, JsonNode.class).getBody();
            Object data = result != null && result.has("data") ? result.get("data") : (result != null ? result : new Object[0]);
            return ResponseEntity.ok(Map.of("data", data, "meta", Map.of("request_id", requestId)));
        } catch (Exception e) {
            log.warn("Public health proxy failed for {}: {}", url, e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0], "meta", Map.of("request_id", requestId)));
        }
    }
}
