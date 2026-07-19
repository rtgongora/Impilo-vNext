package zw.gov.mohcc.impilo.coverage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Coordination-of-benefits decision — persisted payment waterfall (spec §15). */
@Entity
@Table(name = "cv_cob_decisions")
public class CobDecisionEntity {

    @Id @Column(name = "id", nullable = false) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "pod_id", nullable = false, length = 64) private String podId = "national-spine";
    @Column(name = "member_cpid", nullable = false, length = 255) private String memberCpid;
    @Column(name = "benefit_code", length = 64) private String benefitCode;
    @Column(name = "standard_charge", nullable = false) private BigDecimal standardCharge;
    @Column(name = "allowed_amount", nullable = false) private BigDecimal allowedAmount;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "waterfall", nullable = false, columnDefinition = "jsonb") private String waterfall;
    @Column(name = "total_payer_paid", nullable = false) private BigDecimal totalPayerPaid;
    @Column(name = "patient_responsibility", nullable = false) private BigDecimal patientResponsibility;
    @Column(name = "currency", nullable = false, length = 8) private String currency = "USD";
    @Column(name = "ruleset_version", length = 32) private String rulesetVersion;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public CobDecisionEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public void setPodId(String v) { this.podId = v; }
    public String getMemberCpid() { return memberCpid; }
    public void setMemberCpid(String v) { this.memberCpid = v; }
    public String getBenefitCode() { return benefitCode; }
    public void setBenefitCode(String v) { this.benefitCode = v; }
    public BigDecimal getStandardCharge() { return standardCharge; }
    public void setStandardCharge(BigDecimal v) { this.standardCharge = v; }
    public BigDecimal getAllowedAmount() { return allowedAmount; }
    public void setAllowedAmount(BigDecimal v) { this.allowedAmount = v; }
    public String getWaterfall() { return waterfall; }
    public void setWaterfall(String v) { this.waterfall = v; }
    public BigDecimal getTotalPayerPaid() { return totalPayerPaid; }
    public void setTotalPayerPaid(BigDecimal v) { this.totalPayerPaid = v; }
    public BigDecimal getPatientResponsibility() { return patientResponsibility; }
    public void setPatientResponsibility(BigDecimal v) { this.patientResponsibility = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getRulesetVersion() { return rulesetVersion; }
    public void setRulesetVersion(String v) { this.rulesetVersion = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
