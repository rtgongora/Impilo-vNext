package zw.gov.mohcc.impilo.iotingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(schema = "iot", name = "device_registry")
public class DeviceRegistryEntity {

    @Id
    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "external_device_id", nullable = false)
    private String externalDeviceId;

    @Column(name = "device_type", nullable = false)
    private String deviceType;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "model")
    private String model;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "trust_level", nullable = false)
    private String trustLevel = "UNVERIFIED";

    @Column(name = "owner_health_id")
    private String ownerHealthId;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "registered_by", nullable = false)
    private String registeredBy;

    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt = OffsetDateTime.now();

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by")
    private String revokedBy;

    @Column(name = "revocation_reason")
    private String revocationReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities", columnDefinition = "jsonb")
    private List<String> capabilities = List.of();

    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getExternalDeviceId() { return externalDeviceId; }
    public void setExternalDeviceId(String externalDeviceId) { this.externalDeviceId = externalDeviceId; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public String getTrustLevel() { return trustLevel; }
    public void setTrustLevel(String trustLevel) { this.trustLevel = trustLevel; }
    public String getOwnerHealthId() { return ownerHealthId; }
    public void setOwnerHealthId(String ownerHealthId) { this.ownerHealthId = ownerHealthId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(String registeredBy) { this.registeredBy = registeredBy; }
    public OffsetDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(OffsetDateTime registeredAt) { this.registeredAt = registeredAt; }
    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(OffsetDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public String getRevocationReason() { return revocationReason; }
    public void setRevocationReason(String revocationReason) { this.revocationReason = revocationReason; }
    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }
}
