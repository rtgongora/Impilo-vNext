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
@Table(name = "iot_telemetry_dlq")
public class TelemetryDlqEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dlq_id", nullable = false, updatable = false)
    private UUID dlqId;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "error_code", nullable = false, length = 64)
    private String errorCode;

    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "source", nullable = false, length = 32)
    private String source = "HTTP";

    @Column(name = "schema_version", length = 16)
    private String schemaVersion;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TelemetryDlqEntity() {}

    public TelemetryDlqEntity(String deviceId, UUID tenantId, String rawPayload,
                               String errorCode, String errorMessage, String source,
                               String schemaVersion) {
        this.dlqId = UUID.randomUUID();
        this.deviceId = deviceId;
        this.tenantId = tenantId;
        this.rawPayload = rawPayload;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.source = source;
        this.schemaVersion = schemaVersion;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getDlqId() { return dlqId; }
    public String getDeviceId() { return deviceId; }
    public UUID getTenantId() { return tenantId; }
    public String getRawPayload() { return rawPayload; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getSource() { return source; }
    public String getSchemaVersion() { return schemaVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
