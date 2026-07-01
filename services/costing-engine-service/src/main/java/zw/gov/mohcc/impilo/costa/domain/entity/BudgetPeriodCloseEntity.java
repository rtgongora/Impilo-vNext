package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Records a budget's close (and carry-forward) against a financial period. */
@Entity
@Table(name = "costa_budget_period_close")
public class BudgetPeriodCloseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "close_id", nullable = false, unique = true)
    private UUID closeId;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "financial_period_id", nullable = false)
    private UUID financialPeriodId;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "SOFT_CLOSED";

    @Column(name = "closed_by", length = 128)
    private String closedBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "carry_forward_amount", precision = 18, scale = 2)
    private BigDecimal carryForwardAmount;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (closeId == null) closeId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public UUID getCloseId() { return closeId; }
    public void setCloseId(UUID closeId) { this.closeId = closeId; }
    public UUID getBudgetId() { return budgetId; }
    public void setBudgetId(UUID budgetId) { this.budgetId = budgetId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFinancialPeriodId() { return financialPeriodId; }
    public void setFinancialPeriodId(UUID financialPeriodId) { this.financialPeriodId = financialPeriodId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public BigDecimal getCarryForwardAmount() { return carryForwardAmount; }
    public void setCarryForwardAmount(BigDecimal carryForwardAmount) { this.carryForwardAmount = carryForwardAmount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
