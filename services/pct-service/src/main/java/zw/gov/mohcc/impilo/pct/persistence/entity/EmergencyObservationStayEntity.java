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
 * Observation / short-stay (pct V208, W9b) — genuinely absent before this table. Not an admission
 * (no ward bed, no admission record) and not a discharge (the patient has not left). One open stay
 * per episode at a time ({@code uq_emergency_observation_stay_open}).
 */
@Entity
@Table(name = "emergency_observation_stay")
public class EmergencyObservationStayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "stay_id")
    private UUID stayId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "started_by", nullable = false)
    private String startedBy;

    /** Caller-supplied, not derived — see V208's header on why CKP content cannot compute this yet. */
    @Column(name = "min_observation_minutes")
    private Integer minObservationMinutes;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "ended_by")
    private String endedBy;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (startedAt == null) startedAt = now;
        if (createdAt == null) createdAt = now;
    }

    public UUID getStayId() { return stayId; }
    public void setStayId(UUID stayId) { this.stayId = stayId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getEpisodeId() { return episodeId; }
    public void setEpisodeId(UUID episodeId) { this.episodeId = episodeId; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public String getStartedBy() { return startedBy; }
    public void setStartedBy(String startedBy) { this.startedBy = startedBy; }
    public Integer getMinObservationMinutes() { return minObservationMinutes; }
    public void setMinObservationMinutes(Integer minObservationMinutes) { this.minObservationMinutes = minObservationMinutes; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    public String getEndedBy() { return endedBy; }
    public void setEndedBy(String endedBy) { this.endedBy = endedBy; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
