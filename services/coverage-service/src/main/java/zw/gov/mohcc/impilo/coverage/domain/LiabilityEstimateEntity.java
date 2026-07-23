package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Patient-liability estimate (spec §18): Coverage benefit rules over a COSTA price. */
@Entity
@Table(name = "cv_liability_estimates")
public class LiabilityEstimateEntity {

    @Id @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "pod_id", nullable = false, length = 64) private String podId = "national-spine";
    @Column(name = "coverage_id", nullable = false) private UUID coverageId;
    @Column(name = "member_cpid", nullable = false, length = 255) private String memberCpid;
    @Column(name = "facility_id", length = 128) private String facilityId;
    @Column(name = "benefit_code", length = 64) private String benefitCode;
    @Column(name = "standard_charge", nullable = false) private BigDecimal standardCharge;
    @Column(name = "allowed_amount", nullable = false) private BigDecimal allowedAmount;
    @Column(name = "payer_estimate", nullable = false) private BigDecimal payerEstimate;
    @Column(name = "deductible", nullable = false) private BigDecimal deductible = BigDecimal.ZERO;
    @Column(name = "copay", nullable = false) private BigDecimal copay = BigDecimal.ZERO;
    @Column(name = "coinsurance", nullable = false) private BigDecimal coinsurance = BigDecimal.ZERO;
    @Column(name = "non_covered", nullable = false) private BigDecimal nonCovered = BigDecimal.ZERO;
    @Column(name = "patient_responsibility", nullable = false) private BigDecimal patientResponsibility;
    @Column(name = "currency", nullable = false, length = 8) private String currency = "USD";
    @Column(name = "assumptions", columnDefinition = "TEXT") private String assumptions;
    @Column(name = "ruleset_version", length = 32) private String rulesetVersion;
    @Column(name = "expires_at") private OffsetDateTime expiresAt;
    /**
     * OF-B8 (§10.6): the benefit's PA requirement recorded at estimate time —
     * copied from cv_benefit_definitions.requires_authorisation so checkout
     * callers learn the PA posture from the pricing call and the estimate row
     * carries the PA assumption it was computed under (§10.5).
     */
    @Column(name = "requires_authorisation", nullable = false)
    private boolean requiresAuthorisation = false;

    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public LiabilityEstimateEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getPodId() { return podId; }
    public void setPodId(String v) { this.podId = v; }
    public UUID getCoverageId() { return coverageId; }
    public void setCoverageId(UUID v) { this.coverageId = v; }
    public String getMemberCpid() { return memberCpid; }
    public void setMemberCpid(String v) { this.memberCpid = v; }
    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String v) { this.facilityId = v; }
    public String getBenefitCode() { return benefitCode; }
    public void setBenefitCode(String v) { this.benefitCode = v; }
    public BigDecimal getStandardCharge() { return standardCharge; }
    public void setStandardCharge(BigDecimal v) { this.standardCharge = v; }
    public BigDecimal getAllowedAmount() { return allowedAmount; }
    public void setAllowedAmount(BigDecimal v) { this.allowedAmount = v; }
    public BigDecimal getPayerEstimate() { return payerEstimate; }
    public void setPayerEstimate(BigDecimal v) { this.payerEstimate = v; }
    public BigDecimal getDeductible() { return deductible; }
    public void setDeductible(BigDecimal v) { this.deductible = v; }
    public BigDecimal getCopay() { return copay; }
    public void setCopay(BigDecimal v) { this.copay = v; }
    public BigDecimal getCoinsurance() { return coinsurance; }
    public void setCoinsurance(BigDecimal v) { this.coinsurance = v; }
    public BigDecimal getNonCovered() { return nonCovered; }
    public void setNonCovered(BigDecimal v) { this.nonCovered = v; }
    public BigDecimal getPatientResponsibility() { return patientResponsibility; }
    public void setPatientResponsibility(BigDecimal v) { this.patientResponsibility = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getAssumptions() { return assumptions; }
    public void setAssumptions(String v) { this.assumptions = v; }
    public String getRulesetVersion() { return rulesetVersion; }
    public void setRulesetVersion(String v) { this.rulesetVersion = v; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime v) { this.expiresAt = v; }
    public boolean isRequiresAuthorisation() { return requiresAuthorisation; }
    public void setRequiresAuthorisation(boolean v) { this.requiresAuthorisation = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
