package zw.gov.mohcc.impilo.experience.controller.mobile;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;

import java.util.*;

/**
 * Mobile provider notices/alerts endpoints.
 * GET /internal/v1/mobile/provider/notices - return professional notices for the provider
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/notices")
public class MobileNoticesController {

    private final NotificationServiceClient notificationClient;

    public MobileNoticesController(NotificationServiceClient notificationClient) {
        this.notificationClient = notificationClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listNotices(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            var notices = notificationClient.listNotifications(page, size);
            if (notices != null) {
                return ResponseEntity.ok(Map.of(
                        "data", notices,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
                ));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }
}
