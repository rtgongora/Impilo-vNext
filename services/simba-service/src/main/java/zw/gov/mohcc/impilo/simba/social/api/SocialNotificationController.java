package zw.gov.mohcc.impilo.simba.social.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.simba.core.WellnessAccessGuard;
import zw.gov.mohcc.impilo.simba.social.core.SocialNotificationService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialNotificationEventEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wellness-social notifications (sovereign, /internal/v1/wellness/social/notifications/**). Recipients
 * read + mark-read their own inbox (scoped by X-Actor-ID); {@code POST /} enqueues a notification
 * (writes the event + emits the Khuluma-delivery outbox seam).
 */
@RestController
@RequestMapping("/internal/v1/wellness/social/notifications")
public class SocialNotificationController {

    private final SocialNotificationService notifications;

    public SocialNotificationController(SocialNotificationService notifications) {
        this.notifications = notifications;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> notify(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        SocialNotificationService.NotifyRequest in = new SocialNotificationService.NotifyRequest(
                required(body, "recipient_cpid"), actorId,
                required(body, "notification_type"), str(body, "subject_type"),
                uuid(body, "subject_id"), str(body, "summary"));
        SocialNotificationEventEntity n = notifications.notify(tenantId, in);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", n));
    }

    @GetMapping
    public Map<String, Object> inbox(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        requireActor(actorId);
        List<SocialNotificationEventEntity> rows =
                notifications.inbox(tenantId, actorId, cursor, limit);
        Long nextCursor = null;
        if (rows.size() > limit) {
            nextCursor = rows.get(limit - 1).getId();
            rows = rows.subList(0, limit);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("next_cursor", nextCursor);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", rows);
        out.put("meta", meta);
        out.put("unread", notifications.unreadCount(tenantId, actorId));
        return out;
    }

    @PostMapping("/{notificationId}/read")
    public Map<String, Object> markRead(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable("notificationId") UUID notificationId) {
        requireActor(actorId);
        SocialNotificationEventEntity n = notifications.markRead(tenantId, notificationId);
        // A recipient may only mark their own notifications read.
        if (!actorId.equals(n.getRecipientCpid())) {
            throw new WellnessAccessGuard.WellnessAccessDeniedException("Not your notification");
        }
        return Map.of("data", n);
    }

    private static void requireActor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new WellnessAccessGuard.WellnessAccessDeniedException("Actor context required");
        }
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? null : v.toString();
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        String s = str(body, key);
        return (s == null || s.isBlank()) ? null : UUID.fromString(s);
    }
}
