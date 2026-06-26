package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A pre-service admission application (V020). */
@Entity
@Table(name = "lrn_student_application")
public class StudentApplicationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Column(name = "term_id")
    private UUID termId;

    @Column(name = "applicant_subject_type", length = 64)
    private String applicantSubjectType;

    @Column(name = "applicant_subject_id", length = 255)
    private String applicantSubjectId;

    @Column(name = "applicant_name", nullable = false, length = 255)
    private String applicantName;

    @Column(name = "applicant_contact_json", columnDefinition = "TEXT")
    private String applicantContactJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "SUBMITTED";

    @Column(name = "decision_by", length = 255)
    private String decisionBy;

    @Column(name = "decision_at")
    private OffsetDateTime decisionAt;

    @Column(name = "decision_notes", columnDefinition = "TEXT")
    private String decisionNotes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getProgramId() { return programId; }
    public void setProgramId(UUID programId) { this.programId = programId; }
    public UUID getTermId() { return termId; }
    public void setTermId(UUID termId) { this.termId = termId; }
    public String getApplicantSubjectType() { return applicantSubjectType; }
    public void setApplicantSubjectType(String applicantSubjectType) { this.applicantSubjectType = applicantSubjectType; }
    public String getApplicantSubjectId() { return applicantSubjectId; }
    public void setApplicantSubjectId(String applicantSubjectId) { this.applicantSubjectId = applicantSubjectId; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getApplicantContactJson() { return applicantContactJson; }
    public void setApplicantContactJson(String applicantContactJson) { this.applicantContactJson = applicantContactJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDecisionBy() { return decisionBy; }
    public void setDecisionBy(String decisionBy) { this.decisionBy = decisionBy; }
    public OffsetDateTime getDecisionAt() { return decisionAt; }
    public void setDecisionAt(OffsetDateTime decisionAt) { this.decisionAt = decisionAt; }
    public String getDecisionNotes() { return decisionNotes; }
    public void setDecisionNotes(String decisionNotes) { this.decisionNotes = decisionNotes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
