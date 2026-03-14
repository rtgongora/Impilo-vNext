package zw.gov.mohcc.impilo.iotingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "iot_telemetry_readings")
public class TelemetryReadingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reading_id", nullable = false, updatable = false)
    private UUID readingId;

    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "metric_type", nullable = false, length = 128)
    private String metricType;

    @Column(name = "metric_value", nullable = false)
    private double metricValue;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "schema_version", nullable = false, length = 16)
    private String schemaVersion = "1";

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "ingested_at", nullable = false)
    private OffsetDateTime ingestedAt;

    @Column(name = "source", nullable = false, length = 32)
    private String source = "HTTP";

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    protected TelemetryReadingEntity() {}

    public TelemetryReadingEntity(UUID readingId, String deviceId, UUID tenantId,
                                   String metricType, double metricValue, String unit,
                                   String schemaVersion, OffsetDateTime recordedAt, String source) {
        this.readingId = readingId;
        this.deviceId = deviceId;
        this.tenantId = tenantId;
        this.metricType = metricType;
        this.metricValue = metricValue;
        this.unit = unit;
        this.schemaVersion = schemaVersion;
        this.recordedAt = recordedAt;
        this.ingestedAt = OffsetDateTime.now();
        this.source = source;
    }

    public Long getId() { return id; }
    public UUID getReadingId() { return readingId; }
    public String getDeviceId() { return deviceId; }
    public UUID getTenantId() { return tenantId; }
    public String getMetricType() { return metricType; }
    public double getMetricValue() { return metricValue; }
    public String getUnit() { return unit; }
    public String getSchemaVersion() { return schemaVersion; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public OffsetDateTime getIngestedAt() { return ingestedAt; }
    public String getSource() { return source; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
