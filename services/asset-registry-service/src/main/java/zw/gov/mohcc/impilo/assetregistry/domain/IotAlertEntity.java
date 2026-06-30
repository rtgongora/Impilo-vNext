package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** IoT alert DERIVED from real telemetry/heartbeat (offline/threshold/battery/etc).
 *  Never fabricated — only created from actual device signals routed via the consumer/API. */
@Entity
@Table(name = "asr_iot_alert")
public class IotAlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    @Column(name = "alert_type", nullable = false, length = 32)
    private String alertType;

    @Column(name = "metric_type", length = 128)
    private String metricType;

    @Column(name = "observed_value")
    private Double observedValue;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity = "WARN";

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "khuluma_requested", nullable = false)
    private boolean khulumaRequested = false;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt = OffsetDateTime.now();

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    protected IotAlertEntity() {}

    public IotAlertEntity(UUID tenantId, String deviceId, String alertType) {
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.alertType = alertType;
        this.observedAt = OffsetDateTime.now();
    }

    public UUID getAlertId() { return alertId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEquipmentId() { return equipmentId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public String getDeviceId() { return deviceId; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getMetricType() { return metricType; }
    public void setMetricType(String metricType) { this.metricType = metricType; }
    public Double getObservedValue() { return observedValue; }
    public void setObservedValue(Double observedValue) { this.observedValue = observedValue; }
    public Double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(Double thresholdValue) { this.thresholdValue = thresholdValue; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isKhulumaRequested() { return khulumaRequested; }
    public void setKhulumaRequested(boolean khulumaRequested) { this.khulumaRequested = khulumaRequested; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public OffsetDateTime getObservedAt() { return observedAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
}
