package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lrn_moodle_ws_snapshot")
public class MoodleWsSnapshotEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ws_function", nullable = false, length = 160)
    private String wsFunction;

    @Column(name = "course_id", length = 64)
    private String courseId;

    @Column(name = "moodle_user_id", nullable = false, length = 64)
    private String moodleUserId;

    @Column(name = "subject_type", length = 32)
    private String subjectType;

    @Column(name = "subject_id", length = 255)
    private String subjectId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "sync_summary_json", columnDefinition = "TEXT")
    private String syncSummaryJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getWsFunction() {
        return wsFunction;
    }

    public void setWsFunction(String wsFunction) {
        this.wsFunction = wsFunction;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public String getMoodleUserId() {
        return moodleUserId;
    }

    public void setMoodleUserId(String moodleUserId) {
        this.moodleUserId = moodleUserId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }

    public String getSyncSummaryJson() {
        return syncSummaryJson;
    }

    public void setSyncSummaryJson(String syncSummaryJson) {
        this.syncSummaryJson = syncSummaryJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
