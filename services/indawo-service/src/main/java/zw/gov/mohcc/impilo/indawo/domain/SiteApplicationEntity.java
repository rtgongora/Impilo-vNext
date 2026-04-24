package zw.gov.mohcc.impilo.indawo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ind_site_applications")
public class SiteApplicationEntity {

    @Id
    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "application_type", nullable = false, length = 48)
    private String applicationType;

    @Column(name = "priority", nullable = false, length = 16)
    private String priority = "NORMAL";

    @Column(name = "fee_state", nullable = false, length = 16)
    private String feeState = "PENDING";

    @Column(name = "workflow_state", nullable = false, length = 64)
    private String workflowState = "DRAFT";

    @Column(name = "review_state", length = 32)
    private String reviewState;

    @Column(name = "final_decision", length = 32)
    private String finalDecision;

    @Column(name = "decision_date")
    private OffsetDateTime decisionDate;

    @Column(name = "submission_date")
    private OffsetDateTime submissionDate;

    @Column(name = "applicant_name", nullable = false, length = 255)
    private String applicantName;

    @Column(name = "applicant_email", length = 255)
    private String applicantEmail;

    @Column(name = "applicant_phone", length = 64)
    private String applicantPhone;

    @Column(name = "applicant_org", length = 255)
    private String applicantOrg;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "requested_changes", columnDefinition = "JSONB")
    private String requestedChangesJson;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadataJson;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SiteApplicationEntity() {}

    public SiteApplicationEntity(UUID applicationId, UUID tenantId, String applicationType, String applicantName) {
        this.applicationId = applicationId;
        this.tenantId = tenantId;
        this.applicationType = applicationType;
        this.applicantName = applicantName;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getApplicationId() { return applicationId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSiteId() { return siteId; }
    public void setSiteId(UUID siteId) { this.siteId = siteId; }
    public String getApplicationType() { return applicationType; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getFeeState() { return feeState; }
    public void setFeeState(String feeState) { this.feeState = feeState; }
    public String getWorkflowState() { return workflowState; }
    public void setWorkflowState(String workflowState) { this.workflowState = workflowState; }
    public String getReviewState() { return reviewState; }
    public void setReviewState(String reviewState) { this.reviewState = reviewState; }
    public String getFinalDecision() { return finalDecision; }
    public void setFinalDecision(String finalDecision) { this.finalDecision = finalDecision; }
    public OffsetDateTime getDecisionDate() { return decisionDate; }
    public void setDecisionDate(OffsetDateTime decisionDate) { this.decisionDate = decisionDate; }
    public OffsetDateTime getSubmissionDate() { return submissionDate; }
    public void setSubmissionDate(OffsetDateTime submissionDate) { this.submissionDate = submissionDate; }
    public String getApplicantName() { return applicantName; }
    public String getApplicantEmail() { return applicantEmail; }
    public void setApplicantEmail(String applicantEmail) { this.applicantEmail = applicantEmail; }
    public String getApplicantPhone() { return applicantPhone; }
    public void setApplicantPhone(String applicantPhone) { this.applicantPhone = applicantPhone; }
    public String getApplicantOrg() { return applicantOrg; }
    public void setApplicantOrg(String applicantOrg) { this.applicantOrg = applicantOrg; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getRequestedChangesJson() { return requestedChangesJson; }
    public void setRequestedChangesJson(String requestedChangesJson) { this.requestedChangesJson = requestedChangesJson; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void touch(String actorRef) {
        this.updatedBy = actorRef;
        this.updatedAt = OffsetDateTime.now();
    }
}

