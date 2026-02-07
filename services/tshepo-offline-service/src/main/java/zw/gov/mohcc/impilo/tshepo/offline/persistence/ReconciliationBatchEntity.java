package zw.gov.mohcc.impilo.tshepo.offline.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_batch", schema = "tshepo_offline")
public class ReconciliationBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;

    @Column(name = "action_count", nullable = false)
    private int actionCount;

    @Column(name = "reconciled_count", nullable = false)
    private int reconciledCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // --- Getters and Setters ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public int getActionCount() { return actionCount; }
    public void setActionCount(int actionCount) { this.actionCount = actionCount; }
    public int getReconciledCount() { return reconciledCount; }
    public void setReconciledCount(int reconciledCount) { this.reconciledCount = reconciledCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
