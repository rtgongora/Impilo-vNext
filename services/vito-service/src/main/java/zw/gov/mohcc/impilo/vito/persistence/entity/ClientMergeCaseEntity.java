package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_merge_case", schema = "vito")
public class ClientMergeCaseEntity {

    @Id
    @Column(name = "merge_case_id", nullable = false, updatable = false)
    private UUID mergeCaseId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "primary_health_id", nullable = false)
    private UUID primaryHealthId;

    @Column(name = "secondary_health_id", nullable = false)
    private UUID secondaryHealthId;

    @Column(name = "initiated_by", nullable = false)
    private String initiatedBy;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "decision")
    private String decision;

    @Column(name = "survivorship_summary", columnDefinition = "TEXT")
    private String survivorshipSummary;

    @Column(name = "status", nullable = false)
    private String status = "OPEN";

    @Column(name = "linked_match_id")
    private Long linkedMatchId;

    @Column(name = "linked_dedup_case_id")
    private Long linkedDedupCaseId;

    @Column(name = "merge_history_id")
    private Long mergeHistoryId;

    @Column(name = "executed_at")
    private OffsetDateTime executedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (mergeCaseId == null) {
            mergeCaseId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getMergeCaseId() { return mergeCaseId; }
    public void setMergeCaseId(UUID mergeCaseId) { this.mergeCaseId = mergeCaseId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getPrimaryHealthId() { return primaryHealthId; }
    public void setPrimaryHealthId(UUID primaryHealthId) { this.primaryHealthId = primaryHealthId; }
    public UUID getSecondaryHealthId() { return secondaryHealthId; }
    public void setSecondaryHealthId(UUID secondaryHealthId) { this.secondaryHealthId = secondaryHealthId; }
    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getSurvivorshipSummary() { return survivorshipSummary; }
    public void setSurvivorshipSummary(String survivorshipSummary) { this.survivorshipSummary = survivorshipSummary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getLinkedMatchId() { return linkedMatchId; }
    public void setLinkedMatchId(Long linkedMatchId) { this.linkedMatchId = linkedMatchId; }
    public Long getLinkedDedupCaseId() { return linkedDedupCaseId; }
    public void setLinkedDedupCaseId(Long linkedDedupCaseId) { this.linkedDedupCaseId = linkedDedupCaseId; }
    public Long getMergeHistoryId() { return mergeHistoryId; }
    public void setMergeHistoryId(Long mergeHistoryId) { this.mergeHistoryId = mergeHistoryId; }
    public OffsetDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(OffsetDateTime executedAt) { this.executedAt = executedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
