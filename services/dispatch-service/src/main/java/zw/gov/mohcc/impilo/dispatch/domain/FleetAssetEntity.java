package zw.gov.mohcc.impilo.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dsp_fleet_assets")
public class FleetAssetEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "asset_code", nullable = false)
    private String assetCode;

    @Column(name = "asset_type", nullable = false)
    private String assetType;

    @Column(name = "status", nullable = false)
    private String status = "AVAILABLE";

    @Column(name = "plate_number")
    private String plateNumber;

    @Column(name = "capacity_kg")
    private Double capacityKg;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "last_latitude")
    private Double lastLatitude;

    @Column(name = "last_longitude")
    private Double lastLongitude;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected FleetAssetEntity() {}

    public FleetAssetEntity(UUID id, UUID tenantId, String assetCode, String assetType) {
        this.id = id;
        this.tenantId = tenantId;
        this.assetCode = assetCode;
        this.assetType = assetType;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getAssetCode() { return assetCode; }
    public String getAssetType() { return assetType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public Double getCapacityKg() { return capacityKg; }
    public void setCapacityKg(Double capacityKg) { this.capacityKg = capacityKg; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public Double getLastLatitude() { return lastLatitude; }
    public void setLastLatitude(Double lastLatitude) { this.lastLatitude = lastLatitude; }
    public Double getLastLongitude() { return lastLongitude; }
    public void setLastLongitude(Double lastLongitude) { this.lastLongitude = lastLongitude; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
