package zw.gov.mohcc.impilo.gl.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "budget_lines", schema = "gl")
public class BudgetLineEntity {

    @Id
    @Column(name = "budget_id")
    private UUID budgetId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "fiscal_year", nullable = false)
    private int fiscalYear;

    @Column(name = "budget_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal budgetAmount;

    @Column(name = "actual_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal actualAmount = BigDecimal.ZERO;

    @Column(name = "variance", nullable = false, precision = 14, scale = 2)
    private BigDecimal variance = BigDecimal.ZERO;

    @PrePersist
    void prePersist() {
        if (budgetId == null) {
            budgetId = UUID.randomUUID();
        }
        recalcVariance();
    }

    @PreUpdate
    void preUpdate() {
        recalcVariance();
    }

    private void recalcVariance() {
        if (budgetAmount != null && actualAmount != null) {
            variance = budgetAmount.subtract(actualAmount);
        }
    }

    public UUID getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(UUID budgetId) {
        this.budgetId = budgetId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public int getFiscalYear() {
        return fiscalYear;
    }

    public void setFiscalYear(int fiscalYear) {
        this.fiscalYear = fiscalYear;
    }

    public BigDecimal getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(BigDecimal budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public void setActualAmount(BigDecimal actualAmount) {
        this.actualAmount = actualAmount;
    }

    public BigDecimal getVariance() {
        return variance;
    }

    public void setVariance(BigDecimal variance) {
        this.variance = variance;
    }
}
