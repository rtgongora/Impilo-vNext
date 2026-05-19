package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;

import java.util.Map;

/**
 * Comms template governance endpoints backed by notification-service templates.
 */
@RestController
@RequestMapping("/internal/v1/comms/templates")
public class CommsTemplateController {

    private static final Logger log = LoggerFactory.getLogger(CommsTemplateController.class);

    private final NotificationServiceClient notificationClient;

    public CommsTemplateController(NotificationServiceClient notificationClient) {
        this.notificationClient = notificationClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listTemplates(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = notificationClient.listTemplates();
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            log.warn("Comms template list failed: {}", e.getMessage());
            return upstreamFailure("COMMS_TEMPLATE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/{key}")
    public ResponseEntity<Map<String, Object>> getTemplate(
            @PathVariable String key,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = notificationClient.getTemplateByKey(key);
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            log.warn("Comms template get failed: {}", e.getMessage());
            return upstreamFailure("COMMS_TEMPLATE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createTemplate(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = notificationClient.createTemplate(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Comms template create failed: {}", e.getMessage());
            return upstreamFailure("COMMS_TEMPLATE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{templateId}/publish")
    public ResponseEntity<Map<String, Object>> publishTemplate(
            @PathVariable String templateId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = notificationClient.publishTemplate(templateId);
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            log.warn("Comms template publish failed: {}", e.getMessage());
            return upstreamFailure("COMMS_TEMPLATE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{templateId}/retire")
    public ResponseEntity<Map<String, Object>> retireTemplate(
            @PathVariable String templateId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = notificationClient.retireTemplate(templateId);
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            log.warn("Comms template retire failed: {}", e.getMessage());
            return upstreamFailure("COMMS_TEMPLATE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/{templateId}/versions")
    public ResponseEntity<Map<String, Object>> listTemplateVersions(
            @PathVariable String templateId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = notificationClient.listTemplateVersions(templateId);
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            log.warn("Comms template versions list failed: {}", e.getMessage());
            return upstreamFailure("COMMS_TEMPLATE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{templateId}/versions")
    public ResponseEntity<Map<String, Object>> createTemplateVersion(
            @PathVariable String templateId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = notificationClient.createTemplateVersion(templateId, body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Comms template version create failed: {}", e.getMessage());
            return upstreamFailure("COMMS_TEMPLATE_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private ResponseEntity<Map<String, Object>> ok(JsonNode data, String requestId, String correlationId) {
        return ResponseEntity.ok(Map.of(
                "data", data != null ? data : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Comms template upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
