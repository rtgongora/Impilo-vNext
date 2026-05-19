package zw.gov.mohcc.impilo.surv.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ph_data_source_statuses", schema = "surv")
public class PhDataSourceStatusEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source_key", nullable = false)
    private String sourceKey;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "health_status", nullable = false)
    private String healthStatus = "HEALTHY";

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Column(name = "latency_seconds", nullable = false)
    private long latencySeconds = 0;

    @Column(name = "error_count_24h", nullable = false)
    private int errorCount24h = 0;

    @Column(name = "details")
    private String details;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
    public OffsetDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(OffsetDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public long getLatencySeconds() { return latencySeconds; }
    public void setLatencySeconds(long latencySeconds) { this.latencySeconds = latencySeconds; }
    public int getErrorCount24h() { return errorCount24h; }
    public void setErrorCount24h(int errorCount24h) { this.errorCount24h = errorCount24h; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
