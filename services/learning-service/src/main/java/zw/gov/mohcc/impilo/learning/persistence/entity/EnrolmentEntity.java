package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Native Fundo enrolment (Phase 5A). Uniqueness across active states
 * (ENROLLED/IN_PROGRESS) per (tenant, subject, course) is enforced at the
 * database level by a partial unique index in {@code V006} (Postgres) and
 * by an application-layer pre-check in {@code FundoEnrolmentService} so the
 * H2 test profile (which lacks partial indexes) remains consistent.
 */
@Entity
@Table(name = "lrn_enrolment")
public class EnrolmentEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, length = 255)
    private String subjectId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "pathway_id")
    private UUID pathwayId;

    @Column(name = "enrolment_type", nullable = false, length = 32)
    private String enrolmentType = "SELF";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ENROLLED";

    @Column(name = "assigned_by", length = 255)
    private String assignedBy;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Optional academy/org-unit scope (V022); NULL = national/tenant-wide. */
    @Column(name = "learning_space_id")
    private UUID learningSpaceId;

    /** Optional TUSO facility context (V026); facility identity stays owned by TUSO. */
    @Column(name = "facility_id")
    private UUID facilityId;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }
    public UUID getPathwayId() { return pathwayId; }
    public void setPathwayId(UUID pathwayId) { this.pathwayId = pathwayId; }
    public String getEnrolmentType() { return enrolmentType; }
    public void setEnrolmentType(String enrolmentType) { this.enrolmentType = enrolmentType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(OffsetDateTime assignedAt) { this.assignedAt = assignedAt; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public void setDueAt(OffsetDateTime dueAt) { this.dueAt = dueAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getLearningSpaceId() { return learningSpaceId; }
    public void setLearningSpaceId(UUID learningSpaceId) { this.learningSpaceId = learningSpaceId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
}
