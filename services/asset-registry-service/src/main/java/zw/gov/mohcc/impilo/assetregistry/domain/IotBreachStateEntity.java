package zw.gov.mohcc.impilo.assetregistry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consecutive-breach streak state per (tenant, device, threshold-rule). Incremented on each
 * breaching reading and reset on a within-threshold reading, so a THRESHOLD_BREACH alert is
 * raised only once the streak reaches the rule's {@code minCount} (debouncing single spikes).
 */
@Entity
@Table(name = "asr_iot_breach_state")
public class IotBreachStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "state_id")
    private UUID stateId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "consecutive", nullable = false)
    private int consecutive = 0;

    @Column(name = "last_value")
    private Double lastValue;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected IotBreachStateEntity() {}

    public IotBreachStateEntity(UUID tenantId, String deviceId, UUID ruleId) {
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.ruleId = ruleId;
    }

    public UUID getStateId() { return stateId; }
    public UUID getTenantId() { return tenantId; }
    public String getDeviceId() { return deviceId; }
    public UUID getRuleId() { return ruleId; }
    public int getConsecutive() { return consecutive; }
    public void setConsecutive(int consecutive) { this.consecutive = consecutive; }
    public Double getLastValue() { return lastValue; }
    public void setLastValue(Double lastValue) { this.lastValue = lastValue; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
