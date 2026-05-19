package zw.gov.mohcc.impilo.ndila.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ndila_tracking_assets")
public class NdilaTrackingAssetEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "asset_id", nullable = false, length = 255)
    private String assetId;

    @Column(name = "asset_type", nullable = false, length = 48)
    private String assetType;

    @Column(name = "owner_service", length = 64)
    private String ownerService;

    @Column(name = "owner_entity_id", length = 255)
    private String ownerEntityId;

    @Column(name = "label", length = 255)
    private String label;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sensitivity_level", nullable = false, length = 32)
    private String sensitivityLevel = "INTERNAL";

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected NdilaTrackingAssetEntity() {}

    public NdilaTrackingAssetEntity(UUID tenantId, String assetId, String assetType) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.assetId = assetId;
        this.assetType = assetType;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getAssetId() { return assetId; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public String getOwnerService() { return ownerService; }
    public void setOwnerService(String ownerService) { this.ownerService = ownerService; }
    public String getOwnerEntityId() { return ownerEntityId; }
    public void setOwnerEntityId(String ownerEntityId) { this.ownerEntityId = ownerEntityId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getSensitivityLevel() { return sensitivityLevel; }
    public void setSensitivityLevel(String sensitivityLevel) { this.sensitivityLevel = sensitivityLevel; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = OffsetDateTime.now(); }
}
