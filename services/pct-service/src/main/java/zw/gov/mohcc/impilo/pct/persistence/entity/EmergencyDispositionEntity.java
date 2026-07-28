package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The episode-level disposition record (pct V208, W9b) and R12's third mandatory-content gate.
 * NOT a duplicate of {@code ed_disposition} — that table is ed_visit-scoped clinical coding; this is
 * the episode-level routing/closure record every entry route needs, ed_visit or not. See V208's
 * migration header for the full ownership rationale.
 *
 * <p>One row per episode ({@code UNIQUE episode_id}) — a disposition is recorded once, not amended
 * by a second competing row.
 */
@Entity
@Table(name = "emergency_disposition")
public class EmergencyDispositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "disposition_id")
    private UUID dispositionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "disposition_type", nullable = false)
    private String dispositionType;

    @Column(name = "disposition_reason", columnDefinition = "TEXT")
    private String dispositionReason;

    @Column(name = "disposed_by")
    private String disposedBy;

    @Column(name = "disposed_at", nullable = false)
    private OffsetDateTime disposedAt;

    @Column(name = "destination")
    private String destination;

    @Column(name = "cause_or_circumstances", columnDefinition = "TEXT")
    private String causeOrCircumstances;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (disposedAt == null) disposedAt = now;
        if (createdAt == null) createdAt = now;
    }

    public UUID getDispositionId() { return dispositionId; }
    public void setDispositionId(UUID dispositionId) { this.dispositionId = dispositionId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }
    public String getDispositionType() { return dispositionType; }
    public void setDispositionType(String dispositionType) { this.dispositionType = dispositionType; }
    public String getDispositionReason() { return dispositionReason; }
    public void setDispositionReason(String dispositionReason) { this.dispositionReason = dispositionReason; }
    public String getDisposedBy() { return disposedBy; }
    public void setDisposedBy(String disposedBy) { this.disposedBy = disposedBy; }
    public OffsetDateTime getDisposedAt() { return disposedAt; }
    public void setDisposedAt(OffsetDateTime disposedAt) { this.disposedAt = disposedAt; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getCauseOrCircumstances() { return causeOrCircumstances; }
    public void setCauseOrCircumstances(String causeOrCircumstances) { this.causeOrCircumstances = causeOrCircumstances; }
    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(OffsetDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
