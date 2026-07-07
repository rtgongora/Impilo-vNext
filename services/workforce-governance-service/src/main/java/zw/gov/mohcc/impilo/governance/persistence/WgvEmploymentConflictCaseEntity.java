package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An employment EC-number CONFLICT case (IATG WS-D). Opened when a match returns
 * CONFLICT; resolved in-process by an append-only adjudication decision.
 */
@Entity
@Table(name = "wgv_employment_conflict_case")
public class WgvEmploymentConflictCaseEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ec_number", nullable = false, length = 40)
    private String ecNumber;

    @Column(name = "supplied_health_id", length = 255)
    private String suppliedHealthId;

    @Column(name = "subject_ref", nullable = false, length = 255)
    private String subjectRef;

    @Column(name = "conflicting_record_count", nullable = false)
    private int conflictingRecordCount = 1;

    @Column(name = "status", nullable = false, length = 40)
    private String status = "OPEN";

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "adjudication_ref", length = 255)
    private String adjudicationRef;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getEcNumber() { return ecNumber; }
    public void setEcNumber(String ecNumber) { this.ecNumber = ecNumber; }

    public String getSuppliedHealthId() { return suppliedHealthId; }
    public void setSuppliedHealthId(String suppliedHealthId) { this.suppliedHealthId = suppliedHealthId; }

    public String getSubjectRef() { return subjectRef; }
    public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }

    public int getConflictingRecordCount() { return conflictingRecordCount; }
    public void setConflictingRecordCount(int conflictingRecordCount) { this.conflictingRecordCount = conflictingRecordCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(UUID workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }

    public String getAdjudicationRef() { return adjudicationRef; }
    public void setAdjudicationRef(String adjudicationRef) { this.adjudicationRef = adjudicationRef; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getCreatedAt() { return createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
}
