package zw.gov.mohcc.impilo.datagovernance.deid.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registered de-identification dataset (design §"Proposed shape").
 *
 * <p>{@code secretRef} is a key <em>reference</em> into tshepo-keys custody —
 * the per-dataset HMAC secret material itself is never persisted here; it is
 * resolved at run time through the
 * {@link zw.gov.mohcc.impilo.datagovernance.deid.transform.SecretResolver} seam.</p>
 */
@Entity
@Table(name = "deid_dataset")
public class DeidDatasetEntity {

    @Id
    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dataset_code", nullable = false, length = 64)
    private String datasetCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "purpose", nullable = false, length = 64)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_target", nullable = false, length = 16)
    private DeidZone zoneTarget = DeidZone.RELEASED;

    @Column(name = "policy_ref", length = 128)
    private String policyRef;

    @Column(name = "secret_ref", nullable = false, length = 255)
    private String secretRef;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected DeidDatasetEntity() {}

    public DeidDatasetEntity(UUID tenantId, String datasetCode, String name, String purpose,
                             DeidZone zoneTarget, String policyRef, String secretRef) {
        this.tenantId = tenantId;
        this.datasetCode = datasetCode;
        this.name = name;
        this.purpose = purpose;
        this.zoneTarget = zoneTarget;
        this.policyRef = policyRef;
        this.secretRef = secretRef;
    }

    public UUID getDatasetId() { return datasetId; }
    public UUID getTenantId() { return tenantId; }
    public String getDatasetCode() { return datasetCode; }
    public String getName() { return name; }
    public String getPurpose() { return purpose; }
    public DeidZone getZoneTarget() { return zoneTarget; }
    public String getPolicyRef() { return policyRef; }
    public String getSecretRef() { return secretRef; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public void setZoneTarget(DeidZone zoneTarget) { this.zoneTarget = zoneTarget; }
    public void setPolicyRef(String policyRef) { this.policyRef = policyRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }
    public void setStatus(String status) { this.status = status; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
