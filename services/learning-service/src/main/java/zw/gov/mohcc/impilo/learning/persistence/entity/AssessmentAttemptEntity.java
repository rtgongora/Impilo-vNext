package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Native Fundo assessment attempt (Phase 5A). */
@Entity
@Table(
        name = "lrn_assessment_attempt",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_lrn_assessment_attempt_no",
                columnNames = {"assessment_id", "subject_type", "subject_id", "attempt_no"}))
public class AssessmentAttemptEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "enrolment_id")
    private UUID enrolmentId;

    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, length = 255)
    private String subjectId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "score")
    private Integer score;

    @Column(name = "passed")
    private Boolean passed;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "manual_review_status", nullable = false, length = 32)
    private String manualReviewStatus = "PENDING_AUTO";

    @Column(name = "manual_review_score")
    private Integer manualReviewScore;

    @Column(name = "manual_review_passed")
    private Boolean manualReviewPassed;

    @Column(name = "reviewer_id", length = 255)
    private String reviewerId;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "rubric_applied_json", columnDefinition = "TEXT")
    private String rubricAppliedJson;

    @Column(name = "feedback_text", columnDefinition = "TEXT")
    private String feedbackText;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getAssessmentId() { return assessmentId; }
    public void setAssessmentId(UUID assessmentId) { this.assessmentId = assessmentId; }
    public UUID getEnrolmentId() { return enrolmentId; }
    public void setEnrolmentId(UUID enrolmentId) { this.enrolmentId = enrolmentId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public int getAttemptNo() { return attemptNo; }
    public void setAttemptNo(int attemptNo) { this.attemptNo = attemptNo; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public String getManualReviewStatus() { return manualReviewStatus; }
    public void setManualReviewStatus(String manualReviewStatus) { this.manualReviewStatus = manualReviewStatus; }
    public Integer getManualReviewScore() { return manualReviewScore; }
    public void setManualReviewScore(Integer manualReviewScore) { this.manualReviewScore = manualReviewScore; }
    public Boolean getManualReviewPassed() { return manualReviewPassed; }
    public void setManualReviewPassed(Boolean manualReviewPassed) { this.manualReviewPassed = manualReviewPassed; }
    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getRubricAppliedJson() { return rubricAppliedJson; }
    public void setRubricAppliedJson(String rubricAppliedJson) { this.rubricAppliedJson = rubricAppliedJson; }
    public String getFeedbackText() { return feedbackText; }
    public void setFeedbackText(String feedbackText) { this.feedbackText = feedbackText; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
