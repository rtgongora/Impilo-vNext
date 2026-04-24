package zw.gov.mohcc.impilo.msikaflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.msikaflow.domain.AssetStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.AssetType;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mf_delivery_assets")
public class DeliveryAssetEntity {

    @Id
    @Column(name = "asset_id", nullable = false, length = 26)
    private String assetId;

    @Column(name = "provider_id", nullable = false, length = 26)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 30)
    private AssetType assetType;

    @Column(name = "asset_code", nullable = false, length = 100)
    private String assetCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AssetStatus status = AssetStatus.AVAILABLE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capability_summary", nullable = false, columnDefinition = "jsonb")
    private String capabilitySummaryJson = "{}";

    @Column(name = "telemetry_supported", nullable = false)
    private boolean telemetrySupported;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (capabilitySummaryJson == null) capabilitySummaryJson = "{}";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public AssetType getAssetType() { return assetType; }
    public void setAssetType(AssetType assetType) { this.assetType = assetType; }
    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }
    public AssetStatus getStatus() { return status; }
    public void setStatus(AssetStatus status) { this.status = status; }
    public String getCapabilitySummaryJson() { return capabilitySummaryJson; }
    public void setCapabilitySummaryJson(String capabilitySummaryJson) { this.capabilitySummaryJson = capabilitySummaryJson; }
    public boolean isTelemetrySupported() { return telemetrySupported; }
    public void setTelemetrySupported(boolean telemetrySupported) { this.telemetrySupported = telemetrySupported; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

