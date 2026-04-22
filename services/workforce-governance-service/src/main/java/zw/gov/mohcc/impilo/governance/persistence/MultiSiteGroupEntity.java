package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wgv_multi_site_group")
public class MultiSiteGroupEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "organisation_id")
    private UUID organisationId;

    @Column(name = "group_type", nullable = false, length = 64)
    private String groupType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MultiSiteGroupEntity() {}

    public MultiSiteGroupEntity(UUID id, UUID tenantId, String code, String name, String groupType, String status) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.groupType = groupType;
        this.status = status;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; touch(); }
    public String getGroupType() { return groupType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
