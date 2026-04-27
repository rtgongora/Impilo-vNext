package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import zw.gov.mohcc.impilo.costa.domain.enums.BillingTimingMode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "costa_billing_decisions")
public class BillingDecisionEntity {

    @Id
    @Column(name = "billing_decision_id")
    private UUID billingDecisionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cost_estimate_id")
    private UUID costEstimateId;

    @Column(name = "invoice_id", length = 26)
    private String invoiceId;

    @Column(name = "claim_id", length = 80)
    private String claimId;

    @Column(name = "gross_tariffed_amount", precision = 14, scale = 2)
    private BigDecimal grossTariffedAmount;

    @Column(name = "patient_liability", precision = 14, scale = 2)
    private BigDecimal patientLiability;

    @Column(name = "payer_liability", precision = 14, scale = 2)
    private BigDecimal payerLiability;

    @Column(name = "exempted_amount", precision = 14, scale = 2)
    private BigDecimal exemptedAmount;

    @Column(name = "waived_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal waivedAmount = BigDecimal.ZERO;

    @Column(name = "subsidised_amount", precision = 14, scale = 2)
    private BigDecimal subsidisedAmount;

    @Column(name = "unrecovered_amount", precision = 14, scale = 2)
    private BigDecimal unrecoveredAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_codes", nullable = false, columnDefinition = "jsonb")
    private String reasonCodes = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_applied", nullable = false, columnDefinition = "jsonb")
    private String rulesApplied = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lines", nullable = false, columnDefinition = "jsonb")
    private String lines = "[]";

    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired;

    @Column(name = "audit_reference", length = 120)
    private String auditReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadata = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_timing_mode", length = 40)
    private BillingTimingMode billingTimingMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (billingDecisionId == null) {
            billingDecisionId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getBillingDecisionId() {
        return billingDecisionId;
    }

    public void setBillingDecisionId(UUID billingDecisionId) {
        this.billingDecisionId = billingDecisionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getCostEstimateId() {
        return costEstimateId;
    }

    public void setCostEstimateId(UUID costEstimateId) {
        this.costEstimateId = costEstimateId;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getClaimId() {
        return claimId;
    }

    public void setClaimId(String claimId) {
        this.claimId = claimId;
    }

    public BigDecimal getGrossTariffedAmount() {
        return grossTariffedAmount;
    }

    public void setGrossTariffedAmount(BigDecimal grossTariffedAmount) {
        this.grossTariffedAmount = grossTariffedAmount;
    }

    public BigDecimal getPatientLiability() {
        return patientLiability;
    }

    public void setPatientLiability(BigDecimal patientLiability) {
        this.patientLiability = patientLiability;
    }

    public BigDecimal getPayerLiability() {
        return payerLiability;
    }

    public void setPayerLiability(BigDecimal payerLiability) {
        this.payerLiability = payerLiability;
    }

    public BigDecimal getExemptedAmount() {
        return exemptedAmount;
    }

    public void setExemptedAmount(BigDecimal exemptedAmount) {
        this.exemptedAmount = exemptedAmount;
    }

    public BigDecimal getWaivedAmount() {
        return waivedAmount;
    }

    public void setWaivedAmount(BigDecimal waivedAmount) {
        this.waivedAmount = waivedAmount;
    }

    public BigDecimal getSubsidisedAmount() {
        return subsidisedAmount;
    }

    public void setSubsidisedAmount(BigDecimal subsidisedAmount) {
        this.subsidisedAmount = subsidisedAmount;
    }

    public BigDecimal getUnrecoveredAmount() {
        return unrecoveredAmount;
    }

    public void setUnrecoveredAmount(BigDecimal unrecoveredAmount) {
        this.unrecoveredAmount = unrecoveredAmount;
    }

    public String getReasonCodes() {
        return reasonCodes;
    }

    public void setReasonCodes(String reasonCodes) {
        this.reasonCodes = reasonCodes;
    }

    public String getRulesApplied() {
        return rulesApplied;
    }

    public void setRulesApplied(String rulesApplied) {
        this.rulesApplied = rulesApplied;
    }

    public String getLines() {
        return lines;
    }

    public void setLines(String lines) {
        this.lines = lines;
    }

    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }

    public String getAuditReference() {
        return auditReference;
    }

    public void setAuditReference(String auditReference) {
        this.auditReference = auditReference;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public BillingTimingMode getBillingTimingMode() {
        return billingTimingMode;
    }

    public void setBillingTimingMode(BillingTimingMode billingTimingMode) {
        this.billingTimingMode = billingTimingMode;
    }
}
