package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Graduation / qualification award record (V021). Reuses lrn_certificate for the parchment. */
@Entity
@Table(
        name = "lrn_graduation_record",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lrn_graduation_registry", columnNames = {"tenant_id", "registry_number"})
        })
public class GraduationRecordEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_profile_id", nullable = false)
    private UUID studentProfileId;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Column(name = "qualification_title", length = 512)
    private String qualificationTitle;

    @Column(name = "awarded_at")
    private OffsetDateTime awardedAt;

    @Column(name = "registry_number", nullable = false, length = 64)
    private String registryNumber;

    @Column(name = "certificate_id")
    private UUID certificateId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "CONFERRED";

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (awardedAt == null) awardedAt = now;
        if (createdAt == null) createdAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getStudentProfileId() { return studentProfileId; }
    public void setStudentProfileId(UUID studentProfileId) { this.studentProfileId = studentProfileId; }
    public UUID getProgramId() { return programId; }
    public void setProgramId(UUID programId) { this.programId = programId; }
    public String getQualificationTitle() { return qualificationTitle; }
    public void setQualificationTitle(String qualificationTitle) { this.qualificationTitle = qualificationTitle; }
    public OffsetDateTime getAwardedAt() { return awardedAt; }
    public void setAwardedAt(OffsetDateTime awardedAt) { this.awardedAt = awardedAt; }
    public String getRegistryNumber() { return registryNumber; }
    public void setRegistryNumber(String registryNumber) { this.registryNumber = registryNumber; }
    public UUID getCertificateId() { return certificateId; }
    public void setCertificateId(UUID certificateId) { this.certificateId = certificateId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
