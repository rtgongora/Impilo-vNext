package zw.gov.mohcc.impilo.mentalhealth.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mh_safety_plan")
public class SafetyPlanEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "referral_id", nullable = false)
    private UUID referralId;

    @Column(name = "warning_signs")
    private String warningSigns;

    @Column(name = "coping_strategies")
    private String copingStrategies;

    @Column(name = "support_contacts")
    private String supportContacts;

    @Column(name = "means_restriction_notes")
    private String meansRestrictionNotes;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getReferralId() { return referralId; }
    public void setReferralId(UUID v) { this.referralId = v; }
    public String getWarningSigns() { return warningSigns; }
    public void setWarningSigns(String v) { this.warningSigns = v; }
    public String getCopingStrategies() { return copingStrategies; }
    public void setCopingStrategies(String v) { this.copingStrategies = v; }
    public String getSupportContacts() { return supportContacts; }
    public void setSupportContacts(String v) { this.supportContacts = v; }
    public String getMeansRestrictionNotes() { return meansRestrictionNotes; }
    public void setMeansRestrictionNotes(String v) { this.meansRestrictionNotes = v; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String v) { this.createdBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime v) { this.reviewedAt = v; }
}
