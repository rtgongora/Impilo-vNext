package zw.gov.mohcc.impilo.mentalhealth.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Statutory detention/observation. {@code legalBasis} is deliberately free text — see
 * V001__init.sql's rationale on why this pack does not fabricate a Zimbabwe Mental Health Act
 * category picklist.
 */
@Entity
@Table(name = "mh_involuntary_episode")
public class InvoluntaryEpisodeEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "referral_id", nullable = false)
    private UUID referralId;

    @Column(name = "legal_basis", nullable = false)
    private String legalBasis;

    @Column(name = "authorised_by", nullable = false)
    private String authorisedBy;

    @Column(name = "authorised_at", nullable = false)
    private OffsetDateTime authorisedAt;

    @Column(name = "review_due_at")
    private OffsetDateTime reviewDueAt;

    @Column(name = "status", nullable = false)
    private String status = "OPEN";

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "ended_reason")
    private String endedReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getReferralId() { return referralId; }
    public void setReferralId(UUID v) { this.referralId = v; }
    public String getLegalBasis() { return legalBasis; }
    public void setLegalBasis(String v) { this.legalBasis = v; }
    public String getAuthorisedBy() { return authorisedBy; }
    public void setAuthorisedBy(String v) { this.authorisedBy = v; }
    public OffsetDateTime getAuthorisedAt() { return authorisedAt; }
    public void setAuthorisedAt(OffsetDateTime v) { this.authorisedAt = v; }
    public OffsetDateTime getReviewDueAt() { return reviewDueAt; }
    public void setReviewDueAt(OffsetDateTime v) { this.reviewDueAt = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime v) { this.endedAt = v; }
    public String getEndedReason() { return endedReason; }
    public void setEndedReason(String v) { this.endedReason = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
