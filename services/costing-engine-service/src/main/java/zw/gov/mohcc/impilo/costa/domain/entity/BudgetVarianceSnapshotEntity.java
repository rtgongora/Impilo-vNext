package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable budget-vs-actual variance snapshot with an explainable classification. */
@Entity
@Table(name = "costa_budget_variance_snapshot")
public class BudgetVarianceSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variance_id", nullable = false, unique = true)
    private UUID varianceId;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "budget_line_id")
    private UUID budgetLineId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "period_scope", nullable = false, length = 8)
    private String periodScope = "YTD";

    @Column(name = "budgeted", nullable = false, precision = 18, scale = 2)
    private BigDecimal budgeted = BigDecimal.ZERO;

    @Column(name = "committed", nullable = false, precision = 18, scale = 2)
    private BigDecimal committed = BigDecimal.ZERO;

    @Column(name = "actual", nullable = false, precision = 18, scale = 2)
    private BigDecimal actual = BigDecimal.ZERO;

    @Column(name = "available", nullable = false, precision = 18, scale = 2)
    private BigDecimal available = BigDecimal.ZERO;

    @Column(name = "variance_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal varianceAmount = BigDecimal.ZERO;

    @Column(name = "variance_pct", precision = 9, scale = 4)
    private BigDecimal variancePct;

    @Column(name = "classification", nullable = false, length = 24)
    private String classification;

    @Column(name = "driver_note")
    private String driverNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (varianceId == null) varianceId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getVarianceId() { return varianceId; }
    public void setVarianceId(UUID varianceId) { this.varianceId = varianceId; }
    public UUID getBudgetId() { return budgetId; }
    public void setBudgetId(UUID budgetId) { this.budgetId = budgetId; }
    public UUID getBudgetLineId() { return budgetLineId; }
    public void setBudgetLineId(UUID budgetLineId) { this.budgetLineId = budgetLineId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }
    public String getPeriodScope() { return periodScope; }
    public void setPeriodScope(String periodScope) { this.periodScope = periodScope; }
    public BigDecimal getBudgeted() { return budgeted; }
    public void setBudgeted(BigDecimal budgeted) { this.budgeted = budgeted; }
    public BigDecimal getCommitted() { return committed; }
    public void setCommitted(BigDecimal committed) { this.committed = committed; }
    public BigDecimal getActual() { return actual; }
    public void setActual(BigDecimal actual) { this.actual = actual; }
    public BigDecimal getAvailable() { return available; }
    public void setAvailable(BigDecimal available) { this.available = available; }
    public BigDecimal getVarianceAmount() { return varianceAmount; }
    public void setVarianceAmount(BigDecimal varianceAmount) { this.varianceAmount = varianceAmount; }
    public BigDecimal getVariancePct() { return variancePct; }
    public void setVariancePct(BigDecimal variancePct) { this.variancePct = variancePct; }
    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }
    public String getDriverNote() { return driverNote; }
    public void setDriverNote(String driverNote) { this.driverNote = driverNote; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
