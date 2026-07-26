package zw.gov.mohcc.impilo.pct.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A functional assessment. A score OR a reason it could not be scored, never neither. See V106__structured_history.sql. */
@Entity
@Table(name = "pct_functional_assessments")
public class FunctionalAssessmentEntity {

    @Id
    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "subject_cpid")
    private String subjectCpid;

    @Column(name = "assessment_type")
    private String assessmentType;

    @Column(name = "assessment_date")
    private LocalDate assessmentDate;

    @Column(name = "assessor")
    private String assessor;

    @Column(name = "total_score")
    private Integer totalScore;

    @Column(name = "max_score")
    private Integer maxScore;

    @Column(name = "score_absent_reason")
    private String scoreAbsentReason;

    @Column(name = "interpretation")
    private String interpretation;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (assessmentId == null) assessmentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
    }

    public UUID getAssessmentId() { return assessmentId; }
    public void setAssessmentId(UUID v) { this.assessmentId = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getSubjectCpid() { return subjectCpid; }
    public void setSubjectCpid(String v) { this.subjectCpid = v; }
    public String getAssessmentType() { return assessmentType; }
    public void setAssessmentType(String v) { this.assessmentType = v; }
    public LocalDate getAssessmentDate() { return assessmentDate; }
    public void setAssessmentDate(LocalDate v) { this.assessmentDate = v; }
    public String getAssessor() { return assessor; }
    public void setAssessor(String v) { this.assessor = v; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer v) { this.totalScore = v; }
    public Integer getMaxScore() { return maxScore; }
    public void setMaxScore(Integer v) { this.maxScore = v; }
    public String getScoreAbsentReason() { return scoreAbsentReason; }
    public void setScoreAbsentReason(String v) { this.scoreAbsentReason = v; }
    public String getInterpretation() { return interpretation; }
    public void setInterpretation(String v) { this.interpretation = v; }
    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String v) { this.recordedBy = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
