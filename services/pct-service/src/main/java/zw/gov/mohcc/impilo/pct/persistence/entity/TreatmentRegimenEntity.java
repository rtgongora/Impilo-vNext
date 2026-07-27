package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The ART or TB regimen a person is on within a programme enrolment.
 *
 * <p>A regimen change is a new row; the current regimen is the one with no {@link #endedOn}, and
 * there is at most one per enrolment. See {@code V109__treatment_regimen.sql}.</p>
 */
@Entity
@Table(name = "pct_treatment_regimens")
public class TreatmentRegimenEntity {

    @Id
    @Column(name = "regimen_id")
    private UUID regimenId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "enrolment_id", nullable = false)
    private UUID enrolmentId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "regimen_code", nullable = false)
    private String regimenCode;

    @Column(name = "regimen_display")
    private String regimenDisplay;

    @Column(name = "regimen_stage", nullable = false)
    private String regimenStage;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    /** Why this regimen ended. NULL exactly when the regimen is current. */
    @Column(name = "change_reason")
    private String changeReason;

    @Column(name = "notes")
    private String notes;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (regimenId == null) regimenId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (recordedAt == null) recordedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getRegimenId() { return regimenId; }
    public void setRegimenId(UUID regimenId) { this.regimenId = regimenId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getEnrolmentId() { return enrolmentId; }
    public void setEnrolmentId(UUID enrolmentId) { this.enrolmentId = enrolmentId; }

    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }

    public String getRegimenCode() { return regimenCode; }
    public void setRegimenCode(String regimenCode) { this.regimenCode = regimenCode; }

    public String getRegimenDisplay() { return regimenDisplay; }
    public void setRegimenDisplay(String regimenDisplay) { this.regimenDisplay = regimenDisplay; }

    public String getRegimenStage() { return regimenStage; }
    public void setRegimenStage(String regimenStage) { this.regimenStage = regimenStage; }

    public LocalDate getStartedOn() { return startedOn; }
    public void setStartedOn(LocalDate startedOn) { this.startedOn = startedOn; }

    public LocalDate getEndedOn() { return endedOn; }
    public void setEndedOn(LocalDate endedOn) { this.endedOn = endedOn; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }

    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
