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
 * A configured threshold rule for deriving THRESHOLD_BREACH IoT alerts from real ingested
 * metric values (e.g. cold-chain temperature). A reading breaches when it satisfies
 * {@code comparison} against {@code thresholdValue}; an alert is raised once {@code minCount}
 * consecutive breaching readings are observed for a device. Never fabricates telemetry.
 */
@Entity
@Table(name = "asr_iot_threshold_rule")
public class IotThresholdRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "metric_type", nullable = false, length = 128)
    private String metricType;

    /** GT | GTE | LT | LTE — breach when reading <comparison> threshold. */
    @Column(name = "comparison", nullable = false, length = 8)
    private String comparison;

    @Column(name = "threshold_value", nullable = false)
    private double thresholdValue;

    @Column(name = "min_count", nullable = false)
    private int minCount = 1;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity = "WARN";

    /** MADI | PUBLIC_HEALTH | INVENTORY | null (operational only). Cold-chain routes to MADI. */
    @Column(name = "route_to", length = 64)
    private String routeTo;

    @Column(name = "equipment_type", length = 64)
    private String equipmentType;

    @Column(name = "label", length = 256)
    private String label;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected IotThresholdRuleEntity() {}

    public IotThresholdRuleEntity(UUID tenantId, String metricType, String comparison,
                                  double thresholdValue, int minCount) {
        this.tenantId = tenantId;
        this.metricType = metricType;
        this.comparison = comparison;
        this.thresholdValue = thresholdValue;
        this.minCount = minCount;
    }

    /** True when {@code value} breaches this rule's threshold under its comparison operator. */
    public boolean breaches(double value) {
        return switch (comparison == null ? "" : comparison.toUpperCase()) {
            case "GT" -> value > thresholdValue;
            case "GTE" -> value >= thresholdValue;
            case "LT" -> value < thresholdValue;
            case "LTE" -> value <= thresholdValue;
            default -> false;
        };
    }

    public UUID getRuleId() { return ruleId; }
    public UUID getTenantId() { return tenantId; }
    public String getMetricType() { return metricType; }
    public String getComparison() { return comparison; }
    public void setComparison(String comparison) { this.comparison = comparison; }
    public double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(double thresholdValue) { this.thresholdValue = thresholdValue; }
    public int getMinCount() { return minCount; }
    public void setMinCount(int minCount) { this.minCount = minCount; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getRouteTo() { return routeTo; }
    public void setRouteTo(String routeTo) { this.routeTo = routeTo; }
    public String getEquipmentType() { return equipmentType; }
    public void setEquipmentType(String equipmentType) { this.equipmentType = equipmentType; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
