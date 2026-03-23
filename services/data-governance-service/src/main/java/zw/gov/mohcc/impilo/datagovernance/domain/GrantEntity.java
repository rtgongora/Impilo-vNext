package zw.gov.mohcc.impilo.datagovernance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dgv_grant")
public class GrantEntity {

    @Id
    @Column(name = "grant_id", nullable = false)
    private UUID grantId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "principal_id", nullable = false, length = 255)
    private String principalId;

    @Column(name = "purpose_of_use", nullable = false, length = 64)
    private String purposeOfUse;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by", length = 255)
    private String revokedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public GrantEntity() {}

    public GrantEntity(UUID tenantId, UUID datasetId, String principalId,
                       String purposeOfUse, OffsetDateTime expiresAt) {
        this.tenantId = tenantId;
        this.datasetId = datasetId;
        this.principalId = principalId;
        this.purposeOfUse = purposeOfUse;
        this.expiresAt = expiresAt;
    }

    public UUID getGrantId() { return grantId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getDatasetId() { return datasetId; }
    public String getPrincipalId() { return principalId; }
    public String getPurposeOfUse() { return purposeOfUse; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setGrantId(UUID grantId) { this.grantId = grantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }
    public void setPrincipalId(String principalId) { this.principalId = principalId; }
    public void setPurposeOfUse(String purposeOfUse) { this.purposeOfUse = purposeOfUse; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
}
