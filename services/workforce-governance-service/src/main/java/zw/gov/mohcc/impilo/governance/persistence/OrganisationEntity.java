package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wgv_organisation")
public class OrganisationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "organisation_code", nullable = false, length = 64)
    private String organisationCode;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "legal_name", length = 512)
    private String legalName;

    @Column(name = "organisation_type", nullable = false, length = 64)
    private String organisationType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "parent_organisation_id")
    private UUID parentOrganisationId;

    @Column(name = "active_flag", nullable = false)
    private boolean activeFlag = true;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganisationEntity() {}

    public OrganisationEntity(UUID id, UUID tenantId, String organisationCode, String name, String organisationType, String status) {
        this.id = id;
        this.tenantId = tenantId;
        this.organisationCode = organisationCode;
        this.name = name;
        this.organisationType = organisationType;
        this.status = status;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getOrganisationCode() { return organisationCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; touch(); }
    public String getOrganisationType() { return organisationType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public UUID getParentOrganisationId() { return parentOrganisationId; }
    public void setParentOrganisationId(UUID parentOrganisationId) { this.parentOrganisationId = parentOrganisationId; touch(); }
    public boolean isActiveFlag() { return activeFlag; }
    public void setActiveFlag(boolean activeFlag) { this.activeFlag = activeFlag; touch(); }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; touch(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private void touch() { this.updatedAt = Instant.now(); }
}
