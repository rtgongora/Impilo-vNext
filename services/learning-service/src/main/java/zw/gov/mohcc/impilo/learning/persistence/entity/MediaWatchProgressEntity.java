package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-enrolment watch progress on a media asset ({@code lrn_media_watch_progress},
 * V029). {@code watchedSeconds} and {@code furthestPositionSeconds} are MONOTONIC
 * (writers take the max of stored vs reported) so debounced client upserts and
 * redeliveries are idempotent; {@code lastPositionSeconds} is the resume point
 * and may move backwards when the learner scrubs.
 */
@Entity
@Table(
        name = "lrn_media_watch_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_lrn_media_watch_progress",
                columnNames = {"enrolment_id", "asset_id"}))
public class MediaWatchProgressEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "enrolment_id", nullable = false)
    private UUID enrolmentId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "watched_seconds", nullable = false)
    private int watchedSeconds;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "percent", nullable = false)
    private int percent;

    @Column(name = "furthest_position_seconds", nullable = false)
    private int furthestPositionSeconds;

    @Column(name = "last_position_seconds", nullable = false)
    private int lastPositionSeconds;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getEnrolmentId() { return enrolmentId; }
    public void setEnrolmentId(UUID enrolmentId) { this.enrolmentId = enrolmentId; }
    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }
    public int getWatchedSeconds() { return watchedSeconds; }
    public void setWatchedSeconds(int watchedSeconds) { this.watchedSeconds = watchedSeconds; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public int getPercent() { return percent; }
    public void setPercent(int percent) { this.percent = percent; }
    public int getFurthestPositionSeconds() { return furthestPositionSeconds; }
    public void setFurthestPositionSeconds(int furthestPositionSeconds) { this.furthestPositionSeconds = furthestPositionSeconds; }
    public int getLastPositionSeconds() { return lastPositionSeconds; }
    public void setLastPositionSeconds(int lastPositionSeconds) { this.lastPositionSeconds = lastPositionSeconds; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
