package zw.gov.mohcc.impilo.offlineedge.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ofe_reconciliation_batches")
public class ReconciliationBatchEntity {
    @Id @Column(name = "batch_id") private UUID batchId;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "entitlement_id", nullable = false) private UUID entitlementId;
    @Column(name = "total_actions", nullable = false) private int totalActions;
    @Column(name = "replayed_count", nullable = false) private int replayedCount;
    @Column(name = "failed_count", nullable = false) private int failedCount;
    @Column(nullable = false) private String status = "PENDING";
    @Column(name = "started_at") private OffsetDateTime startedAt;
    @Column(name = "completed_at") private OffsetDateTime completedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    protected ReconciliationBatchEntity() {}
    public ReconciliationBatchEntity(UUID batchId, UUID tenantId, UUID entitlementId, int totalActions) {
        this.batchId = batchId; this.tenantId = tenantId; this.entitlementId = entitlementId;
        this.totalActions = totalActions;
    }

    public UUID getBatchId() { return batchId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEntitlementId() { return entitlementId; }
    public int getTotalActions() { return totalActions; }
    public int getReplayedCount() { return replayedCount; }
    public void setReplayedCount(int v) { this.replayedCount = v; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int v) { this.failedCount = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime v) { this.startedAt = v; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime v) { this.completedAt = v; }
}
