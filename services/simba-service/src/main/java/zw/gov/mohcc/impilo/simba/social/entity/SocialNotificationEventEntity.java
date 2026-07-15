package zw.gov.mohcc.impilo.simba.social.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A wellness-social notification event. This is the persisted record; actual delivery is owned by
 * Khuluma (comms). Simba writes the row + emits an outbox event ({@code SIMBA_SOCIAL_NOTIFICATION_
 * REQUESTED} etc.) — the outbox event IS the delivery seam, so there is never fake in-app delivery.
 */
@Entity
@Table(name = "simba_social_notification_event", schema = "simba")
public class SocialNotificationEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false, unique = true)
    private UUID notificationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "recipient_cpid", nullable = false, length = 128)
    private String recipientCpid;

    @Column(name = "actor_cpid", length = 128)
    private String actorCpid;

    @Column(name = "notification_type", nullable = false, length = 32)
    private String notificationType;

    @Column(name = "subject_type", length = 16)
    private String subjectType;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "summary", length = 280)
    private String summary;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "UNREAD";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (notificationId == null) {
            notificationId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getNotificationId() { return notificationId; }
    public void setNotificationId(UUID notificationId) { this.notificationId = notificationId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getRecipientCpid() { return recipientCpid; }
    public void setRecipientCpid(String recipientCpid) { this.recipientCpid = recipientCpid; }
    public String getActorCpid() { return actorCpid; }
    public void setActorCpid(String actorCpid) { this.actorCpid = actorCpid; }
    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public OffsetDateTime getReadAt() { return readAt; }
    public void setReadAt(OffsetDateTime readAt) { this.readAt = readAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
