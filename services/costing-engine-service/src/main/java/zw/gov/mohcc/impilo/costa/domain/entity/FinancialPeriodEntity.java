package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_financial_periods",
        uniqueConstraints = @UniqueConstraint(name = "uq_costa_period",
                columnNames = {"tenant_id", "period_type", "period_year", "period_month"}))
public class FinancialPeriodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_id", nullable = false, unique = true)
    private UUID periodId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "period_type", nullable = false, length = 16)
    private String periodType;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month")
    private Integer periodMonth;

    @Column(name = "period_quarter")
    private Integer periodQuarter;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "status", length = 16)
    private String status = "OPEN";

    @Column(name = "closed_by", length = 128)
    private String closedBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (periodId == null) {
            periodId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getPeriodId() { return periodId; }
    public void setPeriodId(UUID periodId) { this.periodId = periodId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }
    public int getPeriodYear() { return periodYear; }
    public void setPeriodYear(int periodYear) { this.periodYear = periodYear; }
    public Integer getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(Integer periodMonth) { this.periodMonth = periodMonth; }
    public Integer getPeriodQuarter() { return periodQuarter; }
    public void setPeriodQuarter(Integer periodQuarter) { this.periodQuarter = periodQuarter; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
