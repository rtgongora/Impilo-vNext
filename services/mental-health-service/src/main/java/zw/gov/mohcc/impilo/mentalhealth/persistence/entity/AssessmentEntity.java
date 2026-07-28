package zw.gov.mohcc.impilo.mentalhealth.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mh_assessment")
public class AssessmentEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "referral_id", nullable = false)
    private UUID referralId;

    @Column(name = "assessment_type", nullable = false)
    private String assessmentType = "INITIAL";

    @Column(name = "mental_state_exam")
    private String mentalStateExam;

    @Column(name = "capacity_assessed", nullable = false)
    private boolean capacityAssessed;

    @Column(name = "capacity_outcome")
    private String capacityOutcome;

    @Column(name = "assessed_by", nullable = false)
    private String assessedBy;

    @Column(name = "assessed_at", nullable = false)
    private OffsetDateTime assessedAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getReferralId() { return referralId; }
    public void setReferralId(UUID v) { this.referralId = v; }
    public String getAssessmentType() { return assessmentType; }
    public void setAssessmentType(String v) { this.assessmentType = v; }
    public String getMentalStateExam() { return mentalStateExam; }
    public void setMentalStateExam(String v) { this.mentalStateExam = v; }
    public boolean isCapacityAssessed() { return capacityAssessed; }
    public void setCapacityAssessed(boolean v) { this.capacityAssessed = v; }
    public String getCapacityOutcome() { return capacityOutcome; }
    public void setCapacityOutcome(String v) { this.capacityOutcome = v; }
    public String getAssessedBy() { return assessedBy; }
    public void setAssessedBy(String v) { this.assessedBy = v; }
    public OffsetDateTime getAssessedAt() { return assessedAt; }
    public void setAssessedAt(OffsetDateTime v) { this.assessedAt = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
