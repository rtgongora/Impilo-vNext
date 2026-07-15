package zw.gov.mohcc.impilo.simba.social.core;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.core.SimbaEventEmitter;
import zw.gov.mohcc.impilo.simba.social.entity.SocialNotificationEventEntity;
import zw.gov.mohcc.impilo.simba.social.repository.SocialNotificationEventRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wellness-social notifications. Writes {@code simba_social_notification_event} + emits an outbox event
 * per request. Delivery is owned by Khuluma (comms) — Simba never fakes delivery; the outbox event IS
 * the seam. In-app read-state (inbox / mark-read) is Simba's own record.
 *
 * <p>Emitted event types:
 * <ul>
 *   <li>{@code SIMBA_SOCIAL_NOTIFICATION_REQUESTED} — reactions/comments/follows/mentions/invites</li>
 *   <li>{@code SIMBA_GROUP_ANNOUNCEMENT_PUBLISHED} — official group/community announcement fan-out</li>
 *   <li>{@code SIMBA_CHALLENGE_REMINDER_REQUESTED} — challenge nudge</li>
 *   <li>{@code SIMBA_MODERATION_ACTION_TAKEN} — moderation outcome to the affected party</li>
 * </ul>
 */
@Service
public class SocialNotificationService {

    private final SocialNotificationEventRepository repository;
    private final SimbaEventEmitter events;

    public SocialNotificationService(SocialNotificationEventRepository repository,
                                     SimbaEventEmitter events) {
        this.repository = repository;
        this.events = events;
    }

    /** Notification type → the outbox event type it emits. */
    public static String eventTypeFor(String notificationType) {
        String t = notificationType == null ? "" : notificationType.toUpperCase();
        return switch (t) {
            case "ANNOUNCEMENT" -> "SIMBA_GROUP_ANNOUNCEMENT_PUBLISHED";
            case "CHALLENGE_REMINDER" -> "SIMBA_CHALLENGE_REMINDER_REQUESTED";
            case "MODERATION" -> "SIMBA_MODERATION_ACTION_TAKEN";
            default -> "SIMBA_SOCIAL_NOTIFICATION_REQUESTED";
        };
    }

    public record NotifyRequest(String recipientCpid, String actorCpid, String notificationType,
                                String subjectType, UUID subjectId, String summary) { }

    /** Persist a notification event + emit its outbox event (Khuluma consumes to deliver). */
    @Transactional
    public SocialNotificationEventEntity notify(UUID tenantId, NotifyRequest in) {
        if (in.recipientCpid() == null || in.recipientCpid().isBlank()) {
            throw new IllegalArgumentException("recipient_cpid is required");
        }
        SocialNotificationEventEntity n = new SocialNotificationEventEntity();
        n.setTenantId(tenantId);
        n.setRecipientCpid(in.recipientCpid());
        n.setActorCpid(in.actorCpid());
        n.setNotificationType(in.notificationType() == null ? "SYSTEM"
                : in.notificationType().toUpperCase());
        n.setSubjectType(in.subjectType());
        n.setSubjectId(in.subjectId());
        n.setSummary(in.summary());
        n = repository.save(n);

        String eventType = eventTypeFor(n.getNotificationType());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notification_id", n.getNotificationId().toString());
        payload.put("recipient_cpid", n.getRecipientCpid());
        payload.put("notification_type", n.getNotificationType());
        payload.put("subject_type", n.getSubjectType());
        payload.put("subject_id", n.getSubjectId() == null ? null : n.getSubjectId().toString());
        payload.put("summary", n.getSummary());
        // Subject of the outbox event = the recipient (delivery target).
        events.emit(tenantId, "WELLNESS_SOCIAL_NOTIFICATION", n.getNotificationId().toString(),
                "impilo.simba.wellness.social.notification." + eventType.toLowerCase() + ".v1",
                n.getRecipientCpid(), "simba:social-notif:" + n.getNotificationId(), payload);
        return n;
    }

    @Transactional(readOnly = true)
    public List<SocialNotificationEventEntity> inbox(UUID tenantId, String recipientCpid,
                                                     Long cursor, int limit) {
        int capped = Math.max(1, Math.min(limit, 100));
        return repository.inbox(tenantId, recipientCpid, cursor, PageRequest.of(0, capped + 1));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID tenantId, String recipientCpid) {
        return repository.countByTenantIdAndRecipientCpidAndStatus(tenantId, recipientCpid, "UNREAD");
    }

    @Transactional
    public SocialNotificationEventEntity markRead(UUID tenantId, UUID notificationId) {
        SocialNotificationEventEntity n = repository.findByNotificationIdAndTenantId(notificationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        n.setStatus("READ");
        n.setReadAt(OffsetDateTime.now());
        return repository.save(n);
    }
}
