package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A learning-delivery facilitator: trainer / teacher / preceptor / guest (V016).
 * One table with a {@code facilitator_kind} discriminator. {@code subject_type/subject_id}
 * optionally link to a platform person (staff/practitioner) but are nullable so a guest
 * trainer with no platform identity can still be recorded.
 */
@Entity
@Table(name = "lrn_facilitator")
public class FacilitatorEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_type", length = 64)
    private String subjectType;

    @Column(name = "subject_id", length = 255)
    private String subjectId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "facilitator_kind", nullable = false, length = 32)
    private String facilitatorKind = "TRAINER";

    @Column(name = "cadre", length = 120)
    private String cadre;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

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
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getFacilitatorKind() { return facilitatorKind; }
    public void setFacilitatorKind(String facilitatorKind) { this.facilitatorKind = facilitatorKind; }
    public String getCadre() { return cadre; }
    public void setCadre(String cadre) { this.cadre = cadre; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
