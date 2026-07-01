package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Verbal-autopsy interview for a death that occurred outside a health facility (WHO VA standard).
 * It yields a <em>probable</em> cause of death and is deliberately NOT a medical certification:
 * recording a VA only ever sets the death case's {@code cause_of_death_basis} to
 * {@code VERBAL_AUTOPSY_PROBABLE} — it never marks the certificate SIGNED. Medico-legal red flags
 * surfaced during the interview route the case to the coroner/investigation pathway instead of a
 * probable-cause assignment. Owned inside pct-service; no new sovereign service.
 */
@Entity
@Table(name = "pct_verbal_autopsy")
public class VerbalAutopsyEntity {

    @Id
    @Column(name = "va_id", nullable = false)
    private UUID vaId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "instrument", nullable = false)
    private String instrument = "WHO_VA_2022";

    @Column(name = "interviewee_relationship")
    private String intervieweeRelationship;

    @Column(name = "interviewed_at")
    private OffsetDateTime interviewedAt;

    @Column(name = "interviewer_id")
    private String interviewerId;

    @Column(name = "interviewer_role")
    private String interviewerRole;

    @Column(name = "narrative")
    private String narrative;

    @Column(name = "probable_immediate_cause")
    private String probableImmediateCause;

    @Column(name = "probable_underlying_cause")
    private String probableUnderlyingCause;

    @Column(name = "probable_cause_code")
    private String probableCauseCode;

    @Column(name = "cause_assignment_method")
    private String causeAssignmentMethod;

    @Column(name = "red_flags")
    private String redFlags;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "recorded_by_role")
    private String recordedByRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getVaId() { return vaId; }
    public void setVaId(UUID vaId) { this.vaId = vaId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getInstrument() { return instrument; }
    public void setInstrument(String instrument) { this.instrument = instrument; }
    public String getIntervieweeRelationship() { return intervieweeRelationship; }
    public void setIntervieweeRelationship(String intervieweeRelationship) { this.intervieweeRelationship = intervieweeRelationship; }
    public OffsetDateTime getInterviewedAt() { return interviewedAt; }
    public void setInterviewedAt(OffsetDateTime interviewedAt) { this.interviewedAt = interviewedAt; }
    public String getInterviewerId() { return interviewerId; }
    public void setInterviewerId(String interviewerId) { this.interviewerId = interviewerId; }
    public String getInterviewerRole() { return interviewerRole; }
    public void setInterviewerRole(String interviewerRole) { this.interviewerRole = interviewerRole; }
    public String getNarrative() { return narrative; }
    public void setNarrative(String narrative) { this.narrative = narrative; }
    public String getProbableImmediateCause() { return probableImmediateCause; }
    public void setProbableImmediateCause(String probableImmediateCause) { this.probableImmediateCause = probableImmediateCause; }
    public String getProbableUnderlyingCause() { return probableUnderlyingCause; }
    public void setProbableUnderlyingCause(String probableUnderlyingCause) { this.probableUnderlyingCause = probableUnderlyingCause; }
    public String getProbableCauseCode() { return probableCauseCode; }
    public void setProbableCauseCode(String probableCauseCode) { this.probableCauseCode = probableCauseCode; }
    public String getCauseAssignmentMethod() { return causeAssignmentMethod; }
    public void setCauseAssignmentMethod(String causeAssignmentMethod) { this.causeAssignmentMethod = causeAssignmentMethod; }
    public String getRedFlags() { return redFlags; }
    public void setRedFlags(String redFlags) { this.redFlags = redFlags; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
    public String getRecordedByRole() { return recordedByRole; }
    public void setRecordedByRole(String recordedByRole) { this.recordedByRole = recordedByRole; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
