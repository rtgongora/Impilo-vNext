package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_insurance_plans")
public class InsurancePlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "insurer_name", nullable = false, length = 200)
    private String insurerName;

    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Column(name = "plan_name", length = 200)
    private String planName;

    @Column(name = "coverage_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal coveragePct = new BigDecimal("80.00");

    @Column(name = "annual_cap", precision = 14, scale = 2)
    private BigDecimal annualCap;

    @Column(name = "deductible", nullable = false, precision = 14, scale = 2)
    private BigDecimal deductible = BigDecimal.ZERO;

    @Column(name = "copay_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal copayPct = new BigDecimal("20.00");

    @Column(name = "copay_fixed", precision = 14, scale = 2)
    private BigDecimal copayFixed;

    @Column(name = "prior_auth_required", nullable = false)
    private boolean priorAuthRequired = false;

    @Column(name = "covered_categories", columnDefinition = "text[]")
    private String[] coveredCategories = {};

    @Column(name = "excluded_codes", columnDefinition = "text[]")
    private String[] excludedCodes = {};

    @Column(name = "rules", nullable = false, columnDefinition = "jsonb")
    private String rules = "{}";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() { if (createdAt == null) createdAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getInsurerName() { return insurerName; }
    public void setInsurerName(String insurerName) { this.insurerName = insurerName; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public BigDecimal getCoveragePct() { return coveragePct; }
    public void setCoveragePct(BigDecimal coveragePct) { this.coveragePct = coveragePct; }
    public BigDecimal getAnnualCap() { return annualCap; }
    public void setAnnualCap(BigDecimal annualCap) { this.annualCap = annualCap; }
    public BigDecimal getDeductible() { return deductible; }
    public void setDeductible(BigDecimal deductible) { this.deductible = deductible; }
    public BigDecimal getCopayPct() { return copayPct; }
    public void setCopayPct(BigDecimal copayPct) { this.copayPct = copayPct; }
    public BigDecimal getCopayFixed() { return copayFixed; }
    public void setCopayFixed(BigDecimal copayFixed) { this.copayFixed = copayFixed; }
    public boolean isPriorAuthRequired() { return priorAuthRequired; }
    public void setPriorAuthRequired(boolean priorAuthRequired) { this.priorAuthRequired = priorAuthRequired; }
    public String[] getCoveredCategories() { return coveredCategories; }
    public void setCoveredCategories(String[] coveredCategories) { this.coveredCategories = coveredCategories; }
    public String[] getExcludedCodes() { return excludedCodes; }
    public void setExcludedCodes(String[] excludedCodes) { this.excludedCodes = excludedCodes; }
    public String getRules() { return rules; }
    public void setRules(String rules) { this.rules = rules; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
