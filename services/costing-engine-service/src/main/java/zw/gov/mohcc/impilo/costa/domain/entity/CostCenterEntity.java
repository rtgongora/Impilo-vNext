package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_cost_centers")
public class CostCenterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "center_id", nullable = false, unique = true)
    private UUID centerId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "parent_center_id")
    private UUID parentCenterId;

    @Column(name = "center_type", length = 32)
    private String centerType = "DEPARTMENT";

    @Column(name = "budget_amount", precision = 14, scale = 2)
    private BigDecimal budgetAmount;

    @Column(name = "budget_period", length = 16)
    private String budgetPeriod = "ANNUAL";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (centerId == null) {
            centerId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getCenterId() { return centerId; }
    public void setCenterId(UUID centerId) { this.centerId = centerId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public UUID getParentCenterId() { return parentCenterId; }
    public void setParentCenterId(UUID parentCenterId) { this.parentCenterId = parentCenterId; }
    public String getCenterType() { return centerType; }
    public void setCenterType(String centerType) { this.centerType = centerType; }
    public BigDecimal getBudgetAmount() { return budgetAmount; }
    public void setBudgetAmount(BigDecimal budgetAmount) { this.budgetAmount = budgetAmount; }
    public String getBudgetPeriod() { return budgetPeriod; }
    public void setBudgetPeriod(String budgetPeriod) { this.budgetPeriod = budgetPeriod; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
