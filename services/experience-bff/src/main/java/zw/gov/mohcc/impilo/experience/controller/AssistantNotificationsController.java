package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proactive assistant notifications endpoint.
 *
 * <p>Surfaces contextual, work-mode-aware notifications for the ProactiveAssistant
 * UI component. Aggregates signals from guidance, clinical alerts, operational nudges,
 * and wellness prompts. Returns an empty list when no contextual signals are available
 * — the assistant is always non-blocking.</p>
 *
 * <p>Future: wire to guidance-service and clinical-knowledge-platform-service for
 * real-time signal aggregation based on work_mode, facility, and shift context.</p>
 */
@RestController
@RequestMapping("/internal/v1/assistant")
public class AssistantNotificationsController {

    private static final Logger log = LoggerFactory.getLogger(AssistantNotificationsController.class);

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestParam(required = false) String work_mode,
            @RequestParam(required = false) String facility_id,
            @RequestParam(required = false) String shift_id) {

        try {
            List<Map<String, Object>> notifications = buildContextualNotifications(work_mode, tenantId);
            return ResponseEntity.ok(Map.of("data", notifications));
        } catch (Exception e) {
            log.warn("Assistant notifications aggregation failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", List.of()));
        }
    }

    private List<Map<String, Object>> buildContextualNotifications(String workMode, String tenantId) {
        if (workMode == null) {
            return List.of();
        }
        return switch (workMode) {
            case "clinical" -> List.of(
                    notification("OPERATIONAL", "INFO",
                            "Clinical Mode Active",
                            "Patient records, encounters, and orders are available in this mode.",
                            false)
            );
            case "wellness" -> List.of(
                    notification("WELLNESS", "INFO",
                            "Wellness Mode",
                            "Track your health goals, nutrition, and activity in this mode.",
                            true)
            );
            default -> List.of();
        };
    }

    private static Map<String, Object> notification(
            String type, String severity, String title, String body, boolean dismissible) {
        return Map.of(
                "id", UUID.randomUUID().toString(),
                "type", type,
                "severity", severity,
                "title", title,
                "body", body,
                "dismissible", dismissible,
                "timestamp", OffsetDateTime.now().toString(),
                "source", "assistant-bff"
        );
    }
}
