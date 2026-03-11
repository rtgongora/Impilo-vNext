package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "asr_assets")
public class AssetEntity {

    @Id
    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_ref", nullable = false, length = 255)
    private String facilityRef;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "serial_no", length = 255)
    private String serialNo;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "version", nullable = false)
    private int version = 1;

    protected AssetEntity() {}

    public AssetEntity(UUID assetId, UUID tenantId, String facilityRef, String type) {
        this.assetId = assetId;
        this.tenantId = tenantId;
        this.facilityRef = facilityRef;
        this.type = type;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getAssetId() { return assetId; }
    public UUID getTenantId() { return tenantId; }
    public String getFacilityRef() { return facilityRef; }
    public void setFacilityRef(String facilityRef) { this.facilityRef = facilityRef; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
