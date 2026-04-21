package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Provider application entity representing a major lifecycle event application.
 * Tracks the workflow state from draft through to final decision.
 */
@Entity
@Table(name = "provider_applications", schema = "varapi")
public class ProviderApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private ProviderEntity provider;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "application_type", nullable = false, length = 50)
    private String applicationType;

    @Column(name = "workflow_state", nullable = false, length = 50)
    private String workflowState = "DRAFT";

    @Column(name = "priority", length = 20)
    private String priority = "NORMAL";

    @Column(name = "fee_state", length = 20)
    private String feeState = "PENDING";

    @Column(name = "verification_state", length = 20)
    private String verificationState = "PENDING";

    @Column(name = "committee_state", length = 50)
    private String committeeState;

    @Column(name = "final_decision", length = 50)
    private String finalDecision;

    @Column(name = "decision_date")
    private LocalDate decisionDate;

    @Column(name = "authority_route", length = 255)
    private String authorityRoute;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Convenience methods
    public boolean isSubmitted() {
        return "SUBMITTED".equals(workflowState);
    }

    public boolean isApproved() {
        return "DECIDED_APPROVED".equals(workflowState);
    }

    public boolean isClosed() {
        return "CLOSED_OUT".equals(workflowState);
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProviderEntity getProvider() { return provider; }
    public void setProvider(ProviderEntity provider) { this.provider = provider; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getApplicationType() { return applicationType; }
    public void setApplicationType(String applicationType) { this.applicationType = applicationType; }

    public String getWorkflowState() { return workflowState; }
    public void setWorkflowState(String workflowState) { this.workflowState = workflowState; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getFeeState() { return feeState; }
    public void setFeeState(String feeState) { this.feeState = feeState; }

    public String getVerificationState() { return verificationState; }
    public void setVerificationState(String verificationState) { this.verificationState = verificationState; }

    public String getCommitteeState() { return committeeState; }
    public void setCommitteeState(String committeeState) { this.committeeState = committeeState; }

    public String getFinalDecision() { return finalDecision; }
    public void setFinalDecision(String finalDecision) { this.finalDecision = finalDecision; }

    public LocalDate getDecisionDate() { return decisionDate; }
    public void setDecisionDate(LocalDate decisionDate) { this.decisionDate = decisionDate; }

    public String getAuthorityRoute() { return authorityRoute; }
    public void setAuthorityRoute(String authorityRoute) { this.authorityRoute = authorityRoute; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
