package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_budget_allocations")
public class BudgetAllocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "allocation_id", nullable = false, unique = true)
    private UUID allocationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "department_id", length = 128)
    private String departmentId;

    @Column(name = "budget_category", nullable = false, length = 64)
    private String budgetCategory;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "allocated_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal allocatedAmount;

    @Column(name = "spent_amount", precision = 14, scale = 2)
    private BigDecimal spentAmount = BigDecimal.ZERO;

    @Column(name = "committed_amount", precision = 14, scale = 2)
    private BigDecimal committedAmount = BigDecimal.ZERO;

    @Column(name = "available_amount", precision = 14, scale = 2)
    private BigDecimal availableAmount;

    @Column(name = "status", length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (allocationId == null) {
            allocationId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        recomputeAvailable();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
        recomputeAvailable();
    }

    public void recomputeAvailable() {
        BigDecimal alloc = allocatedAmount != null ? allocatedAmount : BigDecimal.ZERO;
        BigDecimal spent = spentAmount != null ? spentAmount : BigDecimal.ZERO;
        BigDecimal committed = committedAmount != null ? committedAmount : BigDecimal.ZERO;
        this.availableAmount = alloc.subtract(spent).subtract(committed);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getAllocationId() { return allocationId; }
    public void setAllocationId(UUID allocationId) { this.allocationId = allocationId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public String getBudgetCategory() { return budgetCategory; }
    public void setBudgetCategory(String budgetCategory) { this.budgetCategory = budgetCategory; }
    public int getPeriodYear() { return periodYear; }
    public void setPeriodYear(int periodYear) { this.periodYear = periodYear; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(BigDecimal allocatedAmount) { this.allocatedAmount = allocatedAmount; }
    public BigDecimal getSpentAmount() { return spentAmount; }
    public void setSpentAmount(BigDecimal spentAmount) { this.spentAmount = spentAmount; }
    public BigDecimal getCommittedAmount() { return committedAmount; }
    public void setCommittedAmount(BigDecimal committedAmount) { this.committedAmount = committedAmount; }
    public BigDecimal getAvailableAmount() { return availableAmount; }
    public void setAvailableAmount(BigDecimal availableAmount) { this.availableAmount = availableAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
