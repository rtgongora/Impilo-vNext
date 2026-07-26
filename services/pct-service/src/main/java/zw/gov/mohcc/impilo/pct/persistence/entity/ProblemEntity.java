package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** An entry on a subject's outpatient problems list (PCT). */
@Entity
@Table(name = "pct_problems")
public class ProblemEntity {

    @Id
    @Column(name = "problem_id")
    private UUID problemId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_cpid", nullable = false)
    private String subjectCpid;

    @Column(name = "journey_id")
    private String journeyId;

    @Column(name = "encounter_id")
    private String encounterId;

    @Column(name = "code")
    private String code;

    @Column(name = "code_system")
    private String codeSystem;

    @Column(name = "display", nullable = false)
    private String display;

    @Column(name = "clinical_status", nullable = false)
    private String clinicalStatus = "ACTIVE";

    @Column(name = "category", nullable = false)
    private String category = "DIAGNOSIS";

    /** Clinician-assessed severity. NULL means not stated — never read as mild. */
    @Column(name = "severity")
    private String severity;

    /**
     * How certain the recorder was, orthogonal to {@link #category}. NULL means never stated and
     * must not be resolved to CONFIRMED.
     */
    @Column(name = "diagnostic_certainty")
    private String diagnosticCertainty;

    @Column(name = "onset_date")
    private LocalDate onsetDate;

    /** When this problem last returned, set alongside a move to RECURRENCE or RELAPSE. */
    @Column(name = "last_recurrence_at")
    private OffsetDateTime lastRecurrenceAt;

    @Column(name = "evidence")
    private String evidence;

    /** The service accountable for this problem. NULL means nobody has taken it. */
    @Column(name = "responsible_service")
    private String responsibleService;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (problemId == null) problemId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getProblemId() { return problemId; }
    public void setProblemId(UUID problemId) { this.problemId = problemId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String subjectCpid) { this.subjectCpid = subjectCpid; }

    public String getJourneyId() { return journeyId; }
    public void setJourneyId(String journeyId) { this.journeyId = journeyId; }

    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getCodeSystem() { return codeSystem; }
    public void setCodeSystem(String codeSystem) { this.codeSystem = codeSystem; }

    public String getDisplay() { return display; }
    public void setDisplay(String display) { this.display = display; }

    public String getClinicalStatus() { return clinicalStatus; }
    public void setClinicalStatus(String clinicalStatus) { this.clinicalStatus = clinicalStatus; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getDiagnosticCertainty() { return diagnosticCertainty; }
    public void setDiagnosticCertainty(String diagnosticCertainty) { this.diagnosticCertainty = diagnosticCertainty; }

    public OffsetDateTime getLastRecurrenceAt() { return lastRecurrenceAt; }
    public void setLastRecurrenceAt(OffsetDateTime lastRecurrenceAt) { this.lastRecurrenceAt = lastRecurrenceAt; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public String getResponsibleService() { return responsibleService; }
    public void setResponsibleService(String responsibleService) { this.responsibleService = responsibleService; }

    public LocalDate getReviewDate() { return reviewDate; }
    public void setReviewDate(LocalDate reviewDate) { this.reviewDate = reviewDate; }

    public LocalDate getOnsetDate() { return onsetDate; }
    public void setOnsetDate(LocalDate onsetDate) { this.onsetDate = onsetDate; }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }

    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
