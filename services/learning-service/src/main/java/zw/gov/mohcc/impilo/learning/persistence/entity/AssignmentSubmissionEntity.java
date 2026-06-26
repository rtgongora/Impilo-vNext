package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A learner's submission to an assignment (V018). The marking block mirrors the
 * AssessmentAttempt manual-review columns so the marking queue/workflow is identical.
 */
@Entity
@Table(
        name = "lrn_assignment_submission",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lrn_assignment_submission",
                        columnNames = {"assignment_id", "subject_type", "subject_id", "submission_no"})
        })
public class AssignmentSubmissionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "enrolment_id")
    private UUID enrolmentId;

    @Column(name = "subject_type", nullable = false, length = 64)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, length = 255)
    private String subjectId;

    @Column(name = "submission_no", nullable = false)
    private int submissionNo = 1;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "media_ref_json", columnDefinition = "TEXT")
    private String mediaRefJson;

    @Column(name = "marking_status", nullable = false, length = 32)
    private String markingStatus = "PENDING_MARK";

    @Column(name = "score")
    private Integer score;

    @Column(name = "passed")
    private Boolean passed;

    @Column(name = "marker_id", length = 255)
    private String markerId;

    @Column(name = "marked_at")
    private OffsetDateTime markedAt;

    @Column(name = "rubric_applied_json", columnDefinition = "TEXT")
    private String rubricAppliedJson;

    @Column(name = "feedback_text", columnDefinition = "TEXT")
    private String feedbackText;

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
        if (submittedAt == null) submittedAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID assignmentId) { this.assignmentId = assignmentId; }
    public UUID getEnrolmentId() { return enrolmentId; }
    public void setEnrolmentId(UUID enrolmentId) { this.enrolmentId = enrolmentId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public int getSubmissionNo() { return submissionNo; }
    public void setSubmissionNo(int submissionNo) { this.submissionNo = submissionNo; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }
    public String getMediaRefJson() { return mediaRefJson; }
    public void setMediaRefJson(String mediaRefJson) { this.mediaRefJson = mediaRefJson; }
    public String getMarkingStatus() { return markingStatus; }
    public void setMarkingStatus(String markingStatus) { this.markingStatus = markingStatus; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }
    public String getMarkerId() { return markerId; }
    public void setMarkerId(String markerId) { this.markerId = markerId; }
    public OffsetDateTime getMarkedAt() { return markedAt; }
    public void setMarkedAt(OffsetDateTime markedAt) { this.markedAt = markedAt; }
    public String getRubricAppliedJson() { return rubricAppliedJson; }
    public void setRubricAppliedJson(String rubricAppliedJson) { this.rubricAppliedJson = rubricAppliedJson; }
    public String getFeedbackText() { return feedbackText; }
    public void setFeedbackText(String feedbackText) { this.feedbackText = feedbackText; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
