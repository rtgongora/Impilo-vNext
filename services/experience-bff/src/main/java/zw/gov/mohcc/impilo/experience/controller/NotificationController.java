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
 * BFF proxy for the notification sovereign service.
 * Exposes notification send, list, read-marking, and preference management.
 */
@RestController
@RequestMapping("/internal/v1/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationServiceClient client;

    public NotificationController(NotificationServiceClient client) {
        this.client = client;
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = client.sendNotification(body);
            return ResponseEntity.status(201).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Notification send failed: {}", e.getMessage());
            return upstreamFailure("NOTIFICATIONS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String recipientId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.listNotifications(recipientId);
            JsonNode content = (data != null && data.has("content")) ? data.get("content") : data;
            return ResponseEntity.ok(Map.of(
                    "data", content != null ? content : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Notification list failed: {}", e.getMessage());
            return upstreamFailure("NOTIFICATIONS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/inbox")
    public ResponseEntity<Map<String, Object>> listInbox(
            @RequestParam(required = false) String recipientId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return list(recipientId, requestId, correlationId);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.markAsRead(id);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Notification mark-as-read failed: {}", e.getMessage());
            return upstreamFailure("NOTIFICATIONS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsReadPost(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return markAsRead(id, requestId, correlationId);
    }

    @PatchMapping("/inbox/{id}/read")
    public ResponseEntity<Map<String, Object>> markInboxAsRead(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return markAsRead(id, requestId, correlationId);
    }

    @PostMapping("/inbox/{id}/read")
    public ResponseEntity<Map<String, Object>> markInboxAsReadPost(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return markAsRead(id, requestId, correlationId);
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> readAll(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.markAllAsRead();
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of("updated", 0),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Notification mark-all-read failed: {}", e.getMessage());
            return upstreamFailure("NOTIFICATIONS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/inbox/read-all")
    public ResponseEntity<Map<String, Object>> inboxReadAll(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return readAll(requestId, correlationId);
    }

    @GetMapping("/inbox/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = client.unreadCount();
            Object count = 0;
            if (data != null && data.has("count")) {
                count = data.get("count").asLong();
            } else if (data != null && data.has("unread")) {
                count = data.get("unread").asLong();
            }
            return ResponseEntity.ok(Map.of(
                    "data", Map.of("count", count),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Notification unread-count failed: {}", e.getMessage());
            return upstreamFailure("NOTIFICATIONS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/preferences")
    public ResponseEntity<Map<String, Object>> getPreferences(
            @RequestParam(required = false) String patientId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = (patientId != null && !patientId.isBlank())
                    ? client.getPreferences(patientId)
                    : client.getPreferences();
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Notification preferences fetch failed: {}", e.getMessage());
            return upstreamFailure("NOTIFICATIONS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PutMapping("/preferences")
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @RequestParam(required = false) String patientId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = (patientId != null && !patientId.isBlank())
                    ? client.updatePreferences(patientId, body)
                    : client.updatePreferences(body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Notification preferences update failed: {}", e.getMessage());
            return upstreamFailure("NOTIFICATIONS_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PutMapping("/preferences/{category}")
    public ResponseEntity<Map<String, Object>> updatePreferenceByCategory(
            @PathVariable String category,
            @RequestParam(required = false) String patientId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> merged = new java.util.LinkedHashMap<>(body);
        merged.putIfAbsent("category", category);
        return updatePreferences(patientId, requestId, correlationId, merged);
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : "Notification upstream unavailable"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
