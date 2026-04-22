package zw.gov.mohcc.impilo.vito.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_verification_review", schema = "vito")
public class ClientVerificationReviewEntity {

    @Id
    @Column(name = "review_id", nullable = false, updatable = false)
    private UUID reviewId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_health_id", nullable = false)
    private UUID clientHealthId;

    @Column(name = "registration_id")
    private UUID registrationId;

    @Column(name = "review_type", nullable = false)
    private String reviewType;

    @Column(name = "status", nullable = false)
    private String status = "OPEN";

    @Column(name = "reviewer")
    private String reviewer;

    @Column(name = "decision")
    private String decision;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (reviewId == null) {
            reviewId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public UUID getReviewId() { return reviewId; }
    public void setReviewId(UUID reviewId) { this.reviewId = reviewId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getClientHealthId() { return clientHealthId; }
    public void setClientHealthId(UUID clientHealthId) { this.clientHealthId = clientHealthId; }
    public UUID getRegistrationId() { return registrationId; }
    public void setRegistrationId(UUID registrationId) { this.registrationId = registrationId; }
    public String getReviewType() { return reviewType; }
    public void setReviewType(String reviewType) { this.reviewType = reviewType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
