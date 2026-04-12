package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cv_appeals")
public class AppealEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "claim_id", nullable = false, length = 100)
    private String claimId;

    @Column(name = "coverage_id")
    private UUID coverageId;

    @Column(name = "appellant_type", length = 32)
    private String appellantType;

    @Column(name = "appellant_id", length = 255)
    private String appellantId;

    @Column(name = "appeal_reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "appeal_evidence", columnDefinition = "JSONB")
    private String evidenceJson;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "decision", length = 30)
    private String decision;

    @Column(name = "decision_notes", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "reviewer_id", length = 255)
    private String reviewerId;

    @Column(name = "decided_by", length = 255)
    private String decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AppealEntity() {}

    public AppealEntity(UUID tenantId, String claimId, UUID coverageId,
                        String appellantType, String appellantId,
                        String reason, String evidenceJson) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.claimId = claimId;
        this.coverageId = coverageId;
        this.appellantType = appellantType;
        this.appellantId = appellantId;
        this.reason = reason;
        this.evidenceJson = evidenceJson != null ? evidenceJson : "{}";
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markUnderReview(String reviewerId) {
        this.status = "UNDER_REVIEW";
        this.reviewerId = reviewerId;
        this.updatedAt = OffsetDateTime.now();
    }

    public void decide(String decision, String decisionReason, String decidedBy) {
        this.decision = decision;
        this.decisionReason = decisionReason;
        this.decidedBy = decidedBy;
        this.decidedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
        if ("OVERTURNED".equals(decision) || "PARTIAL".equals(decision)) {
            this.status = "APPROVED";
        } else if ("UPHELD".equals(decision)) {
            this.status = "DENIED";
        } else {
            this.status = "DENIED";
        }
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getClaimId() { return claimId; }
    public UUID getCoverageId() { return coverageId; }
    public String getAppellantType() { return appellantType; }
    public String getAppellantId() { return appellantId; }
    public String getReason() { return reason; }
    public String getEvidenceJson() { return evidenceJson; }
    public String getStatus() { return status; }
    public String getDecision() { return decision; }
    public String getDecisionReason() { return decisionReason; }
    public String getDecidedBy() { return decidedBy; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public String getReviewerId() { return reviewerId; }
}
