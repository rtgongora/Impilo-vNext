package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Benefit definition on a plan version (spec §13). */
@Entity
@Table(name = "cv_benefit_definitions")
public class BenefitDefinitionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "plan_version_id", nullable = false)
    private UUID planVersionId;

    @Column(name = "benefit_code", nullable = false, length = 64)
    private String benefitCode;

    @Column(name = "category", nullable = false, length = 48)
    private String category;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "coverage_percent", nullable = false)
    private BigDecimal coveragePercent = new BigDecimal("100.00");

    @Column(name = "copay_amount", nullable = false)
    private BigDecimal copayAmount = BigDecimal.ZERO;

    @Column(name = "deductible_amount", nullable = false)
    private BigDecimal deductibleAmount = BigDecimal.ZERO;

    @Column(name = "requires_authorisation", nullable = false)
    private boolean requiresAuthorisation = false;

    @Column(name = "requires_referral", nullable = false)
    private boolean requiresReferral = false;

    @Column(name = "network_requirement", nullable = false, length = 24)
    private String networkRequirement = "ANY";

    @Column(name = "zibo_code_system", length = 64)
    private String ziboCodeSystem;

    @Column(name = "zibo_code", length = 64)
    private String ziboCode;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "USD";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public BenefitDefinitionEntity() {}

    public static BenefitDefinitionEntity create(UUID tenantId, String podId, UUID planVersionId,
                                                 String benefitCode, String category, String description) {
        BenefitDefinitionEntity b = new BenefitDefinitionEntity();
        b.id = UUID.randomUUID();
        b.tenantId = tenantId;
        b.podId = podId != null ? podId : "national-spine";
        b.planVersionId = planVersionId;
        b.benefitCode = benefitCode;
        b.category = category;
        b.description = description;
        return b;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPlanVersionId() { return planVersionId; }
    public String getBenefitCode() { return benefitCode; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public BigDecimal getCoveragePercent() { return coveragePercent; }
    public void setCoveragePercent(BigDecimal v) { this.coveragePercent = v; }
    public BigDecimal getCopayAmount() { return copayAmount; }
    public void setCopayAmount(BigDecimal v) { this.copayAmount = v; }
    public BigDecimal getDeductibleAmount() { return deductibleAmount; }
    public void setDeductibleAmount(BigDecimal v) { this.deductibleAmount = v; }
    public boolean isRequiresAuthorisation() { return requiresAuthorisation; }
    public void setRequiresAuthorisation(boolean v) { this.requiresAuthorisation = v; }
    public boolean isRequiresReferral() { return requiresReferral; }
    public void setRequiresReferral(boolean v) { this.requiresReferral = v; }
    public String getNetworkRequirement() { return networkRequirement; }
    public void setNetworkRequirement(String v) { this.networkRequirement = v; }
    public String getZiboCodeSystem() { return ziboCodeSystem; }
    public void setZiboCodeSystem(String v) { this.ziboCodeSystem = v; }
    public String getZiboCode() { return ziboCode; }
    public void setZiboCode(String v) { this.ziboCode = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
}
