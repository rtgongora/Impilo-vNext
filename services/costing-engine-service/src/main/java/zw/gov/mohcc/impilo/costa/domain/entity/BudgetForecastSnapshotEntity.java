package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable, transparent budget forecast snapshot. All drivers persisted to inputsJson. */
@Entity
@Table(name = "costa_budget_forecast_snapshot")
public class BudgetForecastSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "forecast_id", nullable = false, unique = true)
    private UUID forecastId;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "budget_line_id")
    private UUID budgetLineId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "method", nullable = false, length = 16)
    private String method;

    @Column(name = "elapsed_fraction", precision = 9, scale = 6)
    private BigDecimal elapsedFraction;

    @Column(name = "burn_rate", precision = 18, scale = 4)
    private BigDecimal burnRate;

    @Column(name = "projected_year_end", precision = 18, scale = 2)
    private BigDecimal projectedYearEnd;

    @Column(name = "projected_overrun", precision = 18, scale = 2)
    private BigDecimal projectedOverrun;

    @Column(name = "confidence_note")
    private String confidenceNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inputs_json", nullable = false, columnDefinition = "jsonb")
    private String inputsJson = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (forecastId == null) forecastId = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getForecastId() { return forecastId; }
    public void setForecastId(UUID forecastId) { this.forecastId = forecastId; }
    public UUID getBudgetId() { return budgetId; }
    public void setBudgetId(UUID budgetId) { this.budgetId = budgetId; }
    public UUID getBudgetLineId() { return budgetLineId; }
    public void setBudgetLineId(UUID budgetLineId) { this.budgetLineId = budgetLineId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public BigDecimal getElapsedFraction() { return elapsedFraction; }
    public void setElapsedFraction(BigDecimal elapsedFraction) { this.elapsedFraction = elapsedFraction; }
    public BigDecimal getBurnRate() { return burnRate; }
    public void setBurnRate(BigDecimal burnRate) { this.burnRate = burnRate; }
    public BigDecimal getProjectedYearEnd() { return projectedYearEnd; }
    public void setProjectedYearEnd(BigDecimal projectedYearEnd) { this.projectedYearEnd = projectedYearEnd; }
    public BigDecimal getProjectedOverrun() { return projectedOverrun; }
    public void setProjectedOverrun(BigDecimal projectedOverrun) { this.projectedOverrun = projectedOverrun; }
    public String getConfidenceNote() { return confidenceNote; }
    public void setConfidenceNote(String confidenceNote) { this.confidenceNote = confidenceNote; }
    public String getInputsJson() { return inputsJson; }
    public void setInputsJson(String inputsJson) { this.inputsJson = inputsJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
