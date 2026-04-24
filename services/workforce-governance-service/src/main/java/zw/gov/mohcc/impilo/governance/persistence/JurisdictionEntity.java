package zw.gov.mohcc.impilo.governance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wgv_jurisdiction")
public class JurisdictionEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "jurisdiction_code", nullable = false, length = 64)
    private String jurisdictionCode;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "jurisdiction_type", nullable = false, length = 64)
    private String jurisdictionType;

    @Column(name = "parent_jurisdiction_id")
    private UUID parentJurisdictionId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JurisdictionEntity() {}

    public JurisdictionEntity(UUID id, UUID tenantId, String jurisdictionCode, String name, String jurisdictionType, String status) {
        this.id = id;
        this.tenantId = tenantId;
        this.jurisdictionCode = jurisdictionCode;
        this.name = name;
        this.jurisdictionType = jurisdictionType;
        this.status = status;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getJurisdictionCode() { return jurisdictionCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; touch(); }
    public String getJurisdictionType() { return jurisdictionType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public UUID getParentJurisdictionId() { return parentJurisdictionId; }
    public void setParentJurisdictionId(UUID parentJurisdictionId) { this.parentJurisdictionId = parentJurisdictionId; touch(); }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
