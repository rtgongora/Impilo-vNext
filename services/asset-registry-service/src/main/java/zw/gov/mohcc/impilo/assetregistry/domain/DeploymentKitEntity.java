package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Emergency / outreach / mobile deployment kit. Location is a Ndila reference (not owned). */
@Entity
@Table(name = "asr_deployment_kit")
public class DeploymentKitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "kit_id")
    private UUID kitId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "deployment_ref", length = 255)
    private String deploymentRef;

    @Column(name = "location_ref", length = 255)
    private String locationRef;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "PREPARED";

    @Column(name = "custodian_ref", length = 255)
    private String custodianRef;

    @Column(name = "deployed_at")
    private OffsetDateTime deployedAt;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    protected DeploymentKitEntity() {}

    public DeploymentKitEntity(UUID tenantId, String name) {
        this.tenantId = tenantId;
        this.name = name;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getKitId() { return kitId; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDeploymentRef() { return deploymentRef; }
    public void setDeploymentRef(String deploymentRef) { this.deploymentRef = deploymentRef; }
    public String getLocationRef() { return locationRef; }
    public void setLocationRef(String locationRef) { this.locationRef = locationRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCustodianRef() { return custodianRef; }
    public void setCustodianRef(String custodianRef) { this.custodianRef = custodianRef; }
    public OffsetDateTime getDeployedAt() { return deployedAt; }
    public void setDeployedAt(OffsetDateTime deployedAt) { this.deployedAt = deployedAt; }
    public OffsetDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(OffsetDateTime returnedAt) { this.returnedAt = returnedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
