package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A learning provider (V023) — a binding to an identity owned by another SoR, keyed by
 * {@code provider_kind}: INDIVIDUAL (varapi), ORGANISATION (workforce-governance),
 * FACILITY (TUSO). Never a parallel identity registry.
 */
@Entity
@Table(name = "lrn_learning_provider")
public class LearningProviderEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_kind", nullable = false, length = 32)
    private String providerKind;

    @Column(name = "display_name", nullable = false, length = 512)
    private String displayName;

    @Column(name = "sor_ref", length = 255)
    private String sorRef;

    @Column(name = "council_ref", length = 255)
    private String councilRef;

    @Column(name = "disciplines_json", columnDefinition = "TEXT")
    private String disciplinesJson;

    @Column(name = "branding_ref", length = 1024)
    private String brandingRef;

    @Column(name = "accreditation_status", nullable = false, length = 32)
    private String accreditationStatus = "UNACCREDITED";

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
    public String getProviderKind() { return providerKind; }
    public void setProviderKind(String providerKind) { this.providerKind = providerKind; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getSorRef() { return sorRef; }
    public void setSorRef(String sorRef) { this.sorRef = sorRef; }
    public String getCouncilRef() { return councilRef; }
    public void setCouncilRef(String councilRef) { this.councilRef = councilRef; }
    public String getDisciplinesJson() { return disciplinesJson; }
    public void setDisciplinesJson(String disciplinesJson) { this.disciplinesJson = disciplinesJson; }
    public String getBrandingRef() { return brandingRef; }
    public void setBrandingRef(String brandingRef) { this.brandingRef = brandingRef; }
    public String getAccreditationStatus() { return accreditationStatus; }
    public void setAccreditationStatus(String accreditationStatus) { this.accreditationStatus = accreditationStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
