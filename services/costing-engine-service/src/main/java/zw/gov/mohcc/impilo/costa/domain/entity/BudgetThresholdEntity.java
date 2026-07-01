package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A configurable threshold driving availability control and alerts. */
@Entity
@Table(name = "costa_budget_threshold")
public class BudgetThresholdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "threshold_id", nullable = false, unique = true)
    private UUID thresholdId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "budget_id")
    private UUID budgetId;

    @Column(name = "budget_line_id")
    private UUID budgetLineId;

    @Column(name = "metric", nullable = false, length = 20)
    private String metric;

    @Column(name = "warn_at", nullable = false, precision = 18, scale = 4)
    private BigDecimal warnAt;

    @Column(name = "hard_stop_at", precision = 18, scale = 4)
    private BigDecimal hardStopAt;

    @Column(name = "allow_emergency_override", nullable = false)
    private boolean allowEmergencyOverride = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (thresholdId == null) thresholdId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public UUID getThresholdId() { return thresholdId; }
    public void setThresholdId(UUID thresholdId) { this.thresholdId = thresholdId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getBudgetId() { return budgetId; }
    public void setBudgetId(UUID budgetId) { this.budgetId = budgetId; }
    public UUID getBudgetLineId() { return budgetLineId; }
    public void setBudgetLineId(UUID budgetLineId) { this.budgetLineId = budgetLineId; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public BigDecimal getWarnAt() { return warnAt; }
    public void setWarnAt(BigDecimal warnAt) { this.warnAt = warnAt; }
    public BigDecimal getHardStopAt() { return hardStopAt; }
    public void setHardStopAt(BigDecimal hardStopAt) { this.hardStopAt = hardStopAt; }
    public boolean isAllowEmergencyOverride() { return allowEmergencyOverride; }
    public void setAllowEmergencyOverride(boolean allowEmergencyOverride) { this.allowEmergencyOverride = allowEmergencyOverride; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
