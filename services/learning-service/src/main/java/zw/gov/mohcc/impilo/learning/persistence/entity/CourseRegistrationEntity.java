package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Term course registration (V021) — a thin wrapper over an academic enrolment. */
@Entity
@Table(
        name = "lrn_course_registration",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lrn_course_registration",
                        columnNames = {"student_profile_id", "term_id", "course_id"})
        })
public class CourseRegistrationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_profile_id", nullable = false)
    private UUID studentProfileId;

    @Column(name = "term_id")
    private UUID termId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "enrolment_id")
    private UUID enrolmentId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "REGISTERED";

    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (registeredAt == null) registeredAt = now;
        if (createdAt == null) createdAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getStudentProfileId() { return studentProfileId; }
    public void setStudentProfileId(UUID studentProfileId) { this.studentProfileId = studentProfileId; }
    public UUID getTermId() { return termId; }
    public void setTermId(UUID termId) { this.termId = termId; }
    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID courseId) { this.courseId = courseId; }
    public UUID getEnrolmentId() { return enrolmentId; }
    public void setEnrolmentId(UUID enrolmentId) { this.enrolmentId = enrolmentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(OffsetDateTime registeredAt) { this.registeredAt = registeredAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
