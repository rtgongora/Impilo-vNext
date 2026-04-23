package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Access Channels BFF — bridges Communication, Landela (document services),
 * and notification capabilities into a unified access surface.
 */
@RestController
@RequestMapping("/internal/v1/access")
public class AccessChannelsController {

    private static final Logger log = LoggerFactory.getLogger(AccessChannelsController.class);

    private final RestTemplate restTemplate;
    private final String landelaUrl;
    private final String notificationUrl;

    public AccessChannelsController(
            RestTemplate serviceRestTemplate,
            ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.landelaUrl = endpoints.landelaBaseUrl();
        this.notificationUrl = endpoints.notificationBaseUrl();
    }

    // ── Landela Document Operations ─────────────────────────────────

    @GetMapping("/landela/templates")
    public ResponseEntity<Map<String, Object>> listTemplates(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyGet(landelaUrl + "/internal/v1/templates", requestId, correlationId);
    }

    @PostMapping("/landela/documents/publish")
    public ResponseEntity<Map<String, Object>> publishDocument(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return proxyPost(landelaUrl + "/internal/v1/documents/publish", body, requestId, correlationId);
    }

    @GetMapping("/landela/documents/search")
    public ResponseEntity<Map<String, Object>> searchDocuments(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String query) {
        String url = landelaUrl + "/internal/v1/documents" + (query != null ? "?q=" + query : "");
        return proxyGet(url, requestId, correlationId);
    }

    /**
     * Landela document metadata (canonical gateway path {@code /v1/internal/documents/{id}}).
     */
    @GetMapping("/landela/documents/{documentId}")
    public ResponseEntity<Map<String, Object>> getLandelaDocumentMetadata(
            @PathVariable UUID documentId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String url = landelaUrl + "/v1/internal/documents/" + documentId;
        return proxyGet(url, requestId, correlationId);
    }

    // ── Notification Operations ─────────────────────────────────────

    @PostMapping("/notifications/send")
    public ResponseEntity<Map<String, Object>> sendNotification(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return proxyPost(notificationUrl + "/internal/v1/notifications", body, requestId, correlationId);
    }

    @GetMapping("/notifications/recent")
    public ResponseEntity<Map<String, Object>> recentNotifications(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxyGet(notificationUrl + "/internal/v1/notifications?size=20", requestId, correlationId);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> proxyGet(String url, String requestId, String correlationId) {
        try {
            JsonNode result = restTemplate.getForEntity(url, JsonNode.class).getBody();
            return ResponseEntity.ok(envelope(result, requestId, correlationId));
        } catch (Exception e) {
            log.warn("Access channels GET failed for {}: {}", url, e.getMessage());
            return ResponseEntity.ok(envelope(new Object[0], requestId, correlationId));
        }
    }

    private ResponseEntity<Map<String, Object>> proxyPost(String url, Map<String, Object> body, String requestId, String correlationId) {
        try {
            JsonNode result = restTemplate.postForEntity(url, body, JsonNode.class).getBody();
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(result, requestId, correlationId));
        } catch (Exception e) {
            log.error("Access channels POST failed for {}: {}", url, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", Map.of("code", "ACCESS_CHANNEL_FAILED", "message", e.getMessage())));
        }
    }

    private Map<String, Object> envelope(Object data, String requestId, String correlationId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("data", data != null ? data : new Object[0]);
        r.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return r;
    }
}
