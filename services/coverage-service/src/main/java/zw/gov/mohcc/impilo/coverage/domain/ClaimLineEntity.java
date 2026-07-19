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

/** Claim line — adjudicated independently (spec §19.5). */
@Entity
@Table(name = "cv_claim_lines")
public class ClaimLineEntity {

    @Id @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "claim_id", nullable = false) private UUID claimId;
    @Column(name = "line_number", nullable = false) private int lineNumber = 1;
    @Column(name = "benefit_code", nullable = false, length = 64) private String benefitCode;
    @Column(name = "service_code", length = 64) private String serviceCode;
    @Column(name = "description", length = 255) private String description;
    @Column(name = "quantity", nullable = false) private BigDecimal quantity = BigDecimal.ONE;
    @Column(name = "submitted_amount", nullable = false) private BigDecimal submittedAmount;
    @Column(name = "allowed_amount") private BigDecimal allowedAmount;
    @Column(name = "approved_amount") private BigDecimal approvedAmount;
    @Column(name = "denied_amount") private BigDecimal deniedAmount;
    @Column(name = "copay", nullable = false) private BigDecimal copay = BigDecimal.ZERO;
    @Column(name = "coinsurance", nullable = false) private BigDecimal coinsurance = BigDecimal.ZERO;
    @Column(name = "patient_responsibility", nullable = false) private BigDecimal patientResponsibility = BigDecimal.ZERO;
    @Column(name = "line_status", nullable = false, length = 24) private String lineStatus = "SUBMITTED";
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "reason_codes", columnDefinition = "jsonb") private String reasonCodes;
    @Column(name = "authorisation_line_id") private UUID authorisationLineId;
    @Column(name = "consume_ref", length = 128) private String consumeRef;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt = OffsetDateTime.now();

    public ClaimLineEntity() {}

    public static ClaimLineEntity create(UUID tenantId, UUID claimId, int lineNumber, String benefitCode,
                                         BigDecimal submittedAmount) {
        ClaimLineEntity l = new ClaimLineEntity();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.claimId = claimId;
        l.lineNumber = lineNumber;
        l.benefitCode = benefitCode;
        l.submittedAmount = submittedAmount;
        return l;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getClaimId() { return claimId; }
    public int getLineNumber() { return lineNumber; }
    public String getBenefitCode() { return benefitCode; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String v) { this.serviceCode = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal v) { this.quantity = v; }
    public BigDecimal getSubmittedAmount() { return submittedAmount; }
    public BigDecimal getAllowedAmount() { return allowedAmount; }
    public void setAllowedAmount(BigDecimal v) { this.allowedAmount = v; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal v) { this.approvedAmount = v; }
    public BigDecimal getDeniedAmount() { return deniedAmount; }
    public void setDeniedAmount(BigDecimal v) { this.deniedAmount = v; }
    public BigDecimal getCopay() { return copay; }
    public void setCopay(BigDecimal v) { this.copay = v; }
    public BigDecimal getCoinsurance() { return coinsurance; }
    public void setCoinsurance(BigDecimal v) { this.coinsurance = v; }
    public BigDecimal getPatientResponsibility() { return patientResponsibility; }
    public void setPatientResponsibility(BigDecimal v) { this.patientResponsibility = v; }
    public String getLineStatus() { return lineStatus; }
    public void setLineStatus(String v) { this.lineStatus = v; this.updatedAt = OffsetDateTime.now(); }
    public String getReasonCodes() { return reasonCodes; }
    public void setReasonCodes(String v) { this.reasonCodes = v; }
    public UUID getAuthorisationLineId() { return authorisationLineId; }
    public void setAuthorisationLineId(UUID v) { this.authorisationLineId = v; }
    public String getConsumeRef() { return consumeRef; }
    public void setConsumeRef(String v) { this.consumeRef = v; }
}
