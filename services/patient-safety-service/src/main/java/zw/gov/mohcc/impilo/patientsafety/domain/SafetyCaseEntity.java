package zw.gov.mohcc.impilo.patientsafety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Regulatory safety case opened when a report is submitted. Lifecycle:
 * OPEN → TRIAGED → FOLLOW_UP_REQUESTED → AWAITING_FOLLOW_UP → READY_FOR_VIGIFLOW →
 * MANUAL_ENTRY_COMPLETED → EXPORT_READY → DISPATCH_PENDING/FAILED → ACKNOWLEDGED/REJECTED/CLOSED.
 */
@Entity
@Table(name = "ps_safety_case")
public class SafetyCaseEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";
    @Column(name = "report_id", nullable = false)
    private UUID reportId;
    @Column(name = "case_reference", nullable = false, length = 32)
    private String caseReference;
    @Column(name = "status", nullable = false, length = 32)
    private String status = "OPEN";
    @Column(name = "priority", nullable = false, length = 16)
    private String priority = "ROUTINE";    // ROUTINE | HIGH | URGENT
    @Column(name = "report_type", nullable = false, length = 16)
    private String reportType;
    @Column(name = "is_serious", nullable = false)
    private boolean serious = false;
    @Column(name = "assigned_reviewer_id", length = 255)
    private String assignedReviewerId;
    @Column(name = "causality_assessment", nullable = false, length = 32)
    private String causalityAssessment = "NOT_ASSESSED";
    @Column(name = "mcaz_status", length = 255)
    private String mcazStatus;
    @Column(name = "triaged_at")
    private OffsetDateTime triagedAt;
    @Column(name = "closed_at")
    private OffsetDateTime closedAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SafetyCaseEntity() {}

    public SafetyCaseEntity(UUID tenantId, String podId, UUID reportId, String caseReference,
                            String reportType, boolean serious, String priority) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        if (podId != null && !podId.isBlank()) this.podId = podId;
        this.reportId = reportId;
        this.caseReference = caseReference;
        this.reportType = reportType;
        this.serious = serious;
        this.priority = priority;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void touch() { this.updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public UUID getReportId() { return reportId; }
    public String getCaseReference() { return caseReference; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority = v; }
    public String getReportType() { return reportType; }
    public boolean isSerious() { return serious; }
    public void setSerious(boolean v) { this.serious = v; }
    public String getAssignedReviewerId() { return assignedReviewerId; }
    public void setAssignedReviewerId(String v) { this.assignedReviewerId = v; }
    public String getCausalityAssessment() { return causalityAssessment; }
    public void setCausalityAssessment(String v) { this.causalityAssessment = v; }
    public String getMcazStatus() { return mcazStatus; }
    public void setMcazStatus(String v) { this.mcazStatus = v; }
    public OffsetDateTime getTriagedAt() { return triagedAt; }
    public void setTriagedAt(OffsetDateTime v) { this.triagedAt = v; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime v) { this.closedAt = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
