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
 * Academic student identity (V020) — created on admission. Distinct from
 * {@code lrn_user_learning_profile} (in-service learner/CPD record); linked to a
 * person by subject_type/subject_id, no FK.
 */
@Entity
@Table(
        name = "lrn_student_profile",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lrn_student_profile_number", columnNames = {"tenant_id", "student_number"})
        })
public class StudentProfileEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_type", length = 64)
    private String subjectType;

    @Column(name = "subject_id", length = 255)
    private String subjectId;

    @Column(name = "student_number", nullable = false, length = 64)
    private String studentNumber;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Column(name = "admission_application_id")
    private UUID admissionApplicationId;

    @Column(name = "admitted_at")
    private OffsetDateTime admittedAt;

    @Column(name = "current_term_id")
    private UUID currentTermId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ADMITTED";

    @Column(name = "created_by", length = 255)
    private String createdBy;

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
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }
    public UUID getProgramId() { return programId; }
    public void setProgramId(UUID programId) { this.programId = programId; }
    public UUID getAdmissionApplicationId() { return admissionApplicationId; }
    public void setAdmissionApplicationId(UUID admissionApplicationId) { this.admissionApplicationId = admissionApplicationId; }
    public OffsetDateTime getAdmittedAt() { return admittedAt; }
    public void setAdmittedAt(OffsetDateTime admittedAt) { this.admittedAt = admittedAt; }
    public UUID getCurrentTermId() { return currentTermId; }
    public void setCurrentTermId(UUID currentTermId) { this.currentTermId = currentTermId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
