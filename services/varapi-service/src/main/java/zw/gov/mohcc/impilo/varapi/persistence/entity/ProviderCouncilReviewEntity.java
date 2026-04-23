package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_council_reviews", schema = "varapi")
public class ProviderCouncilReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private ProviderApplicationEntity application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "council_id")
    private CouncilEntity council;

    @Column(name = "review_type", nullable = false, length = 50)
    private String reviewType;

    @Column(name = "reviewer_actor_id", nullable = false, length = 255)
    private String reviewerActorId;

    @Column(name = "decision", length = 40)
    private String decision;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt = Instant.now();

    @Column(name = "resolution_reference", length = 255)
    private String resolutionReference;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public ProviderApplicationEntity getApplication() { return application; }
    public void setApplication(ProviderApplicationEntity application) { this.application = application; }
    public CouncilEntity getCouncil() { return council; }
    public void setCouncil(CouncilEntity council) { this.council = council; }
    public String getReviewType() { return reviewType; }
    public void setReviewType(String reviewType) { this.reviewType = reviewType; }
    public String getReviewerActorId() { return reviewerActorId; }
    public void setReviewerActorId(String reviewerActorId) { this.reviewerActorId = reviewerActorId; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getResolutionReference() { return resolutionReference; }
    public void setResolutionReference(String resolutionReference) { this.resolutionReference = resolutionReference; }
}
