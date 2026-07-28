package zw.gov.mohcc.impilo.mentalhealth.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A restraint-REDUCTION register row. See V001__init.sql for the full rationale. */
@Entity
@Table(name = "mh_restraint_event")
public class RestraintEventEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "referral_id", nullable = false)
    private UUID referralId;

    @Column(name = "restraint_type", nullable = false)
    private String restraintType;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "de_escalation_attempted", nullable = false)
    private String deEscalationAttempted;

    @Column(name = "initiated_by", nullable = false)
    private String initiatedBy;

    @Column(name = "initiated_at", nullable = false)
    private OffsetDateTime initiatedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_outcome")
    private String reviewOutcome;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getReferralId() { return referralId; }
    public void setReferralId(UUID v) { this.referralId = v; }
    public String getRestraintType() { return restraintType; }
    public void setRestraintType(String v) { this.restraintType = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getDeEscalationAttempted() { return deEscalationAttempted; }
    public void setDeEscalationAttempted(String v) { this.deEscalationAttempted = v; }
    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String v) { this.initiatedBy = v; }
    public OffsetDateTime getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(OffsetDateTime v) { this.initiatedAt = v; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime v) { this.endedAt = v; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String v) { this.reviewedBy = v; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime v) { this.reviewedAt = v; }
    public String getReviewOutcome() { return reviewOutcome; }
    public void setReviewOutcome(String v) { this.reviewOutcome = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
