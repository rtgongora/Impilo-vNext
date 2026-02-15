package zw.gov.mohcc.impilo.ndr.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ndr_dataset_access_policies")
public class DatasetAccessPolicyEntity {

    @Id
    @Column(name = "policy_id", nullable = false, length = 26)
    private String policyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dataset_id", nullable = false, length = 26)
    private String datasetId;

    @Column(name = "principal_type", nullable = false, length = 40)
    private String principalType = "ROLE";

    @Column(name = "principal_id", nullable = false, length = 128)
    private String principalId;

    @Column(name = "permission", nullable = false, length = 40)
    private String permission = "READ";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and setters

    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }

    public String getPrincipalType() { return principalType; }
    public void setPrincipalType(String principalType) { this.principalType = principalType; }

    public String getPrincipalId() { return principalId; }
    public void setPrincipalId(String principalId) { this.principalId = principalId; }

    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
