package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Provider committee review entity for tracking committee decisions.
 */
@Entity
@Table(name = "provider_committee_reviews", schema = "varapi")
public class ProviderCommitteeReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_application_id")
    private ProviderApplicationEntity relatedApplication;

    @Column(name = "case_id")
    private Long caseId;

    @Column(name = "committee_type", nullable = false, length = 50)
    private String committeeType;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "decision", length = 50)
    private String decision;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "resolution_reference", length = 255)
    private String resolutionReference;

    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ProviderEntity getProvider() { return provider; }
    public void setProvider(ProviderEntity provider) { this.provider = provider; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public ProviderApplicationEntity getRelatedApplication() { return relatedApplication; }
    public void setRelatedApplication(ProviderApplicationEntity relatedApplication) { this.relatedApplication = relatedApplication; }

    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }

    public String getCommitteeType() { return committeeType; }
    public void setCommitteeType(String committeeType) { this.committeeType = committeeType; }

    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getResolutionReference() { return resolutionReference; }
    public void setResolutionReference(String resolutionReference) { this.resolutionReference = resolutionReference; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
