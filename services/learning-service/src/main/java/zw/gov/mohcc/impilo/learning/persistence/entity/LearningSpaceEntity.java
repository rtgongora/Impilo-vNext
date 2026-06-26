package zw.gov.mohcc.impilo.learning.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A learning space — academy / school / training-centre / department (V023). Binds to a
 * workforce-governance OrganisationUnit ({@code org_unit_ref}); owned by a provider.
 */
@Entity
@Table(name = "lrn_learning_space")
public class LearningSpaceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "org_unit_ref", length = 255)
    private String orgUnitRef;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "space_kind", nullable = false, length = 32)
    private String spaceKind = "ACADEMY";

    @Column(name = "disciplines_json", columnDefinition = "TEXT")
    private String disciplinesJson;

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
    public UUID getProviderId() { return providerId; }
    public void setProviderId(UUID providerId) { this.providerId = providerId; }
    public String getOrgUnitRef() { return orgUnitRef; }
    public void setOrgUnitRef(String orgUnitRef) { this.orgUnitRef = orgUnitRef; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSpaceKind() { return spaceKind; }
    public void setSpaceKind(String spaceKind) { this.spaceKind = spaceKind; }
    public String getDisciplinesJson() { return disciplinesJson; }
    public void setDisciplinesJson(String disciplinesJson) { this.disciplinesJson = disciplinesJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
