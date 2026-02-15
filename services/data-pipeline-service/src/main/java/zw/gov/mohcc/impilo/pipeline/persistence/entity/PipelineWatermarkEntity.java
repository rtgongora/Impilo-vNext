package zw.gov.mohcc.impilo.pipeline.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-source high-water mark for tracking ingestion progress.
 */
@Entity
@Table(name = "dp_pipeline_watermarks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "source_id"}))
public class PipelineWatermarkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "watermark_id")
    private Long watermarkId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    @Column(name = "last_event_id", nullable = false, length = 64)
    private String lastEventId;

    @Column(name = "last_occurred_at", nullable = false)
    private OffsetDateTime lastOccurredAt;

    @Column(name = "event_count", nullable = false)
    private long eventCount;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and setters

    public Long getWatermarkId() { return watermarkId; }
    public void setWatermarkId(Long watermarkId) { this.watermarkId = watermarkId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getLastEventId() { return lastEventId; }
    public void setLastEventId(String lastEventId) { this.lastEventId = lastEventId; }

    public OffsetDateTime getLastOccurredAt() { return lastOccurredAt; }
    public void setLastOccurredAt(OffsetDateTime lastOccurredAt) { this.lastOccurredAt = lastOccurredAt; }

    public long getEventCount() { return eventCount; }
    public void setEventCount(long eventCount) { this.eventCount = eventCount; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
