package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A pre-service academic program / qualification (V019), distinct from a course. */
@Entity
@Table(
        name = "lrn_academic_program",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lrn_academic_program_code", columnNames = {"tenant_id", "code"})
        })
public class AcademicProgramEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 160)
    private String code;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "qualification_level", length = 64)
    private String qualificationLevel;

    @Column(name = "duration_terms")
    private Integer durationTerms;

    @Column(name = "cadre_target", length = 120)
    private String cadreTarget;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Optional academy/org-unit scope (V022); NULL = national/tenant-wide. */
    @Column(name = "learning_space_id")
    private UUID learningSpaceId;

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
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getQualificationLevel() { return qualificationLevel; }
    public void setQualificationLevel(String qualificationLevel) { this.qualificationLevel = qualificationLevel; }
    public Integer getDurationTerms() { return durationTerms; }
    public void setDurationTerms(Integer durationTerms) { this.durationTerms = durationTerms; }
    public String getCadreTarget() { return cadreTarget; }
    public void setCadreTarget(String cadreTarget) { this.cadreTarget = cadreTarget; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public UUID getLearningSpaceId() { return learningSpaceId; }
    public void setLearningSpaceId(UUID learningSpaceId) { this.learningSpaceId = learningSpaceId; }
}
