package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin proxy for notification-service template CRUD.
 */
@RestController
@RequestMapping("/internal/v1/notifications/templates")
public class NotificationTemplateController {

    private static final Logger log = LoggerFactory.getLogger(NotificationTemplateController.class);

    private final NotificationServiceClient client;

    public NotificationTemplateController(NotificationServiceClient client) {
        this.client = client;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listTemplates(), requestId, correlationId, false);
    }

    @GetMapping("/{key}")
    public ResponseEntity<Map<String, Object>> getByKey(
            @PathVariable String key,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.getTemplateByKey(key), requestId, correlationId, false);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.createTemplate(body), requestId, correlationId, true);
    }

    @PostMapping("/{templateId}/publish")
    public ResponseEntity<Map<String, Object>> publish(
            @PathVariable String templateId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.publishTemplate(templateId), requestId, correlationId, false);
    }

    @PostMapping("/{templateId}/retire")
    public ResponseEntity<Map<String, Object>> retire(
            @PathVariable String templateId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.retireTemplate(templateId), requestId, correlationId, false);
    }

    @GetMapping("/{templateId}/versions")
    public ResponseEntity<Map<String, Object>> versions(
            @PathVariable String templateId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listTemplateVersions(templateId), requestId, correlationId, false);
    }

    private ResponseEntity<Map<String, Object>> proxy(
            java.util.function.Supplier<JsonNode> call,
            String requestId,
            String correlationId,
            boolean created) {
        try {
            JsonNode data = call.get();
            HttpStatus status = created ? HttpStatus.CREATED : HttpStatus.OK;
            return ResponseEntity.status(status).body(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.warn("Notification template proxy failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "NOTIFICATION_UNAVAILABLE", "message", e.getMessage()),
                    "meta", meta(requestId, correlationId)));
        }
    }

    private static Map<String, String> meta(String requestId, String correlationId) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("request_id", requestId);
        m.put("correlation_id", correlationId);
        return m;
    }
}
