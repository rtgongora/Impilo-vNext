package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wgv_organisation_unit")
public class OrganisationUnitEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(name = "parent_unit_id")
    private UUID parentUnitId;

    @Column(name = "unit_code", nullable = false, length = 64)
    private String unitCode;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "unit_type", nullable = false, length = 64)
    private String unitType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganisationUnitEntity() {}

    public OrganisationUnitEntity(UUID id, UUID tenantId, UUID organisationId, String unitCode, String name, String unitType, String status) {
        this.id = id;
        this.tenantId = tenantId;
        this.organisationId = organisationId;
        this.unitCode = unitCode;
        this.name = name;
        this.unitType = unitType;
        this.status = status;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getOrganisationId() { return organisationId; }
    public UUID getParentUnitId() { return parentUnitId; }
    public void setParentUnitId(UUID parentUnitId) { this.parentUnitId = parentUnitId; touch(); }
    public String getUnitCode() { return unitCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }
    public String getUnitType() { return unitType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public boolean isActiveFlag() { return activeFlag; }
    public void setActiveFlag(boolean activeFlag) { this.activeFlag = activeFlag; touch(); }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
