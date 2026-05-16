package zw.gov.mohcc.impilo.ndila.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ndila_tracking_events")
public class NdilaTrackingEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId = UUID.randomUUID();

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

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "altitude_meters", precision = 8, scale = 2)
    private BigDecimal altitudeMeters;

    @Column(name = "speed_mps", precision = 8, scale = 2)
    private BigDecimal speedMps;

    @Column(name = "heading_degrees", precision = 6, scale = 2)
    private BigDecimal headingDegrees;

    @Column(name = "accuracy_meters", precision = 8, scale = 2)
    private BigDecimal accuracyMeters;

    @Column(name = "battery_level", precision = 5, scale = 2)
    private BigDecimal batteryLevel;

    @Column(name = "network_type", length = 16)
    private String networkType;

    @Column(name = "telemetry_source", nullable = false, length = 32)
    private String telemetrySource = "MOBILE_APP";

    @Column(name = "event_timestamp", nullable = false)
    private OffsetDateTime eventTimestamp;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt = OffsetDateTime.now();

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadataJson;

    protected NdilaTrackingEventEntity() {}

    public NdilaTrackingEventEntity(UUID tenantId, String assetId, String assetType,
                                    BigDecimal latitude, BigDecimal longitude,
                                    OffsetDateTime eventTimestamp) {
        this.tenantId = tenantId;
        this.assetId = assetId;
        this.assetType = assetType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.eventTimestamp = eventTimestamp;
    }

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getTenantId() { return tenantId; }
    public String getAssetId() { return assetId; }
    public String getAssetType() { return assetType; }
    public String getOwnerService() { return ownerService; }
    public void setOwnerService(String ownerService) { this.ownerService = ownerService; }
    public String getOwnerEntityId() { return ownerEntityId; }
    public void setOwnerEntityId(String ownerEntityId) { this.ownerEntityId = ownerEntityId; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getAltitudeMeters() { return altitudeMeters; }
    public void setAltitudeMeters(BigDecimal altitudeMeters) { this.altitudeMeters = altitudeMeters; }
    public BigDecimal getSpeedMps() { return speedMps; }
    public void setSpeedMps(BigDecimal speedMps) { this.speedMps = speedMps; }
    public BigDecimal getHeadingDegrees() { return headingDegrees; }
    public void setHeadingDegrees(BigDecimal headingDegrees) { this.headingDegrees = headingDegrees; }
    public BigDecimal getAccuracyMeters() { return accuracyMeters; }
    public void setAccuracyMeters(BigDecimal accuracyMeters) { this.accuracyMeters = accuracyMeters; }
    public BigDecimal getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(BigDecimal batteryLevel) { this.batteryLevel = batteryLevel; }
    public String getNetworkType() { return networkType; }
    public void setNetworkType(String networkType) { this.networkType = networkType; }
    public String getTelemetrySource() { return telemetrySource; }
    public void setTelemetrySource(String telemetrySource) { this.telemetrySource = telemetrySource; }
    public OffsetDateTime getEventTimestamp() { return eventTimestamp; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
