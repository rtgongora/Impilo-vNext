package zw.gov.mohcc.impilo.rito.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Moderation / dispute record for a rating — SUBMITTED → UNDER_REVIEW →
 * PUBLISHED | WITHHELD | DISPUTED → RESOLVED. Manipulation flags (coordinated /
 * retaliation / review-bombing / duplicate) gate publication before a rating reaches a
 * summary. Disputes and appeals link back to a governed {@code rit_case}.
 */
@Entity
@Table(name = "rit_rating_moderation", schema = "rito")
public class RatingModerationEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "rating_id", nullable = false)
    private UUID ratingId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "state", nullable = false, length = 24)
    private String state = "SUBMITTED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manipulation_flags", nullable = false, columnDefinition = "jsonb")
    private String manipulationFlags = "[]";

    @Column(name = "reviewer_actor_id", length = 128)
    private String reviewerActorId;

    @Column(name = "decision", length = 32)
    private String decision;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "dispute_case_id")
    private UUID disputeCaseId;

    @Column(name = "appeal_reference", length = 128)
    private String appealReference;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getRatingId() { return ratingId; }
    public void setRatingId(UUID ratingId) { this.ratingId = ratingId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getManipulationFlags() { return manipulationFlags; }
    public void setManipulationFlags(String manipulationFlags) { this.manipulationFlags = manipulationFlags; }
    public String getReviewerActorId() { return reviewerActorId; }
    public void setReviewerActorId(String reviewerActorId) { this.reviewerActorId = reviewerActorId; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public UUID getDisputeCaseId() { return disputeCaseId; }
    public void setDisputeCaseId(UUID disputeCaseId) { this.disputeCaseId = disputeCaseId; }
    public String getAppealReference() { return appealReference; }
    public void setAppealReference(String appealReference) { this.appealReference = appealReference; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
