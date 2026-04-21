package zw.gov.mohcc.impilo.tuso.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "committee_review", schema = "tuso")
public class CommitteeReviewEntity {

    @Id
    @Column(name = "review_id", nullable = false, updatable = false)
    private UUID reviewId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityEntity facility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private FacilityApplicationEntity application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id")
    private FacilityInspectionEntity inspection;

    @Column(name = "committee_type", nullable = false, length = 64)
    private String committeeType;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 64)
    private CommitteeDecision decision;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "resolution_reference", length = 128)
    private String resolutionReference;

    @Column(name = "authority_context", length = 128)
    private String authorityContext;

    @Column(name = "decided_by", nullable = false, length = 255)
    private String decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @PrePersist
    void onCreate() {
        if (reviewId == null) {
            reviewId = UUID.randomUUID();
        }
        if (decidedAt == null) {
            decidedAt = Instant.now();
        }
    }

    public UUID getReviewId() { return reviewId; }
    public void setReviewId(UUID reviewId) { this.reviewId = reviewId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public FacilityEntity getFacility() { return facility; }
    public void setFacility(FacilityEntity facility) { this.facility = facility; }
    public FacilityApplicationEntity getApplication() { return application; }
    public void setApplication(FacilityApplicationEntity application) { this.application = application; }
    public FacilityInspectionEntity getInspection() { return inspection; }
    public void setInspection(FacilityInspectionEntity inspection) { this.inspection = inspection; }
    public String getCommitteeType() { return committeeType; }
    public void setCommitteeType(String committeeType) { this.committeeType = committeeType; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public CommitteeDecision getDecision() { return decision; }
    public void setDecision(CommitteeDecision decision) { this.decision = decision; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getResolutionReference() { return resolutionReference; }
    public void setResolutionReference(String resolutionReference) { this.resolutionReference = resolutionReference; }
    public String getAuthorityContext() { return authorityContext; }
    public void setAuthorityContext(String authorityContext) { this.authorityContext = authorityContext; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
