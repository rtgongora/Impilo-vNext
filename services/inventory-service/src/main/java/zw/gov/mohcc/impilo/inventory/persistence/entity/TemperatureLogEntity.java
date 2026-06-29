package zw.gov.mohcc.impilo.inventory.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.inventory.domain.TemperatureSource;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A temperature reading for a cold-chain location.
 */
@Entity
@Table(name = "inv_temperature_logs")
public class TemperatureLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "log_id", nullable = false)
    private UUID logId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cc_location_id", nullable = false)
    private UUID ccLocationId;

    @Column(name = "temperature", nullable = false)
    private double temperature;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private TemperatureSource source = TemperatureSource.MANUAL;

    @Column(name = "within_range", nullable = false)
    private boolean withinRange;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (recordedAt == null) {
            recordedAt = createdAt;
        }
    }

    public UUID getLogId() { return logId; }
    public void setLogId(UUID logId) { this.logId = logId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getCcLocationId() { return ccLocationId; }
    public void setCcLocationId(UUID ccLocationId) { this.ccLocationId = ccLocationId; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public TemperatureSource getSource() { return source; }
    public void setSource(TemperatureSource source) { this.source = source; }

    public boolean isWithinRange() { return withinRange; }
    public void setWithinRange(boolean withinRange) { this.withinRange = withinRange; }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }

    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
