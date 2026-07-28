package zw.gov.mohcc.impilo.mentalhealth.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mh_followup")
public class FollowupEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "referral_id", nullable = false)
    private UUID referralId;

    @Column(name = "followup_type", nullable = false)
    private String followupType = "CLINIC_REVIEW";

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Column(name = "scheduled_by", nullable = false)
    private String scheduledBy;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "outcome_notes")
    private String outcomeNotes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getReferralId() { return referralId; }
    public void setReferralId(UUID v) { this.referralId = v; }
    public String getFollowupType() { return followupType; }
    public void setFollowupType(String v) { this.followupType = v; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime v) { this.scheduledAt = v; }
    public String getScheduledBy() { return scheduledBy; }
    public void setScheduledBy(String v) { this.scheduledBy = v; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime v) { this.completedAt = v; }
    public String getOutcomeNotes() { return outcomeNotes; }
    public void setOutcomeNotes(String v) { this.outcomeNotes = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
