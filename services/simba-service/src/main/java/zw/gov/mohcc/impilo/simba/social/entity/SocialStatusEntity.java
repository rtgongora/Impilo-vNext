package zw.gov.mohcc.impilo.simba.social.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An ephemeral wellness-social status (a short, expiring "how I'm doing" note). Distinct from a
 * {@link SocialPostEntity}: statuses are transient, carry a mood, and auto-hide at {@code expiresAt}.
 * Persisted to the pre-existing {@code simba.simba_social_status} table (migration V008 social_core).
 */
@Entity
@Table(name = "simba_social_status", schema = "simba")
public class SocialStatusEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "status_id", nullable = false, unique = true)
    private UUID statusId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_cpid", nullable = false, length = 128)
    private String actorCpid;

    @Column(name = "actor_type", nullable = false, length = 24)
    private String actorType = "CLIENT";

    @Column(name = "text", nullable = false, length = 280)
    private String text;

    @Column(name = "mood", length = 24)
    private String mood;

    @Column(name = "visibility", nullable = false, length = 32)
    private String visibility = "PRIVATE";

    @Column(name = "moderation_status", nullable = false, length = 16)
    private String moderationStatus = "VISIBLE";

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (statusId == null) {
            statusId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public UUID getStatusId() { return statusId; }
    public void setStatusId(UUID statusId) { this.statusId = statusId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getActorCpid() { return actorCpid; }
    public void setActorCpid(String actorCpid) { this.actorCpid = actorCpid; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getModerationStatus() { return moderationStatus; }
    public void setModerationStatus(String moderationStatus) { this.moderationStatus = moderationStatus; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
