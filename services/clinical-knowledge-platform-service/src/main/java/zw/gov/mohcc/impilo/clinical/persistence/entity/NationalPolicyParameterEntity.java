package zw.gov.mohcc.impilo.clinical.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A governed number that is policy, not engineering — e.g. the age below which a young person's
 * sexual and reproductive health record is confidential from her guardian.
 *
 * <p>Two properties make this different from configuration. A {@code RATIFIED} row must be
 * {@code VERIFIED} (enforced by CHECK in V041), so a seeded guess cannot become authoritative by a
 * status flip. And the effective window is explicit, so a decision taken in the past stays
 * re-explainable against the version that was in force then, not against today's value.
 */
@Entity
@Table(schema = "clinical", name = "national_policy_parameters")
public class NationalPolicyParameterEntity {

    public static final String APPROVAL_ENGINEERING_SEED = "ENGINEERING_SEED";
    public static final String APPROVAL_RATIFIED = "RATIFIED";
    public static final String APPROVAL_WITHDRAWN = "WITHDRAWN";

    public static final String VERIFICATION_UNVERIFIED = "UNVERIFIED";
    public static final String VERIFICATION_VERIFIED = "VERIFIED";

    @Id @Column(name = "id") private UUID id;
    @Column(name = "parameter_code") private String parameterCode;
    @Column(name = "domain") private String domain;
    @Column(name = "value_type") private String valueType;
    @Column(name = "value_text") private String valueText;
    @Column(name = "unit") private String unit;
    @Column(name = "effective_start") private LocalDate effectiveStart;
    @Column(name = "effective_end") private LocalDate effectiveEnd;
    @Column(name = "version") private Integer version;
    @Column(name = "approval_status") private String approvalStatus;
    @Column(name = "verification_status") private String verificationStatus;
    @Column(name = "legal_basis") private String legalBasis;
    @Column(name = "content_version") private String contentVersion;
    @Column(name = "created_at") private OffsetDateTime createdAt;

    /**
     * Whether a consumer may treat this parameter as settled national policy. A seed is readable —
     * consumers need to see that a value exists and is unverified — but it is not authority.
     */
    public boolean isRatified() {
        return APPROVAL_RATIFIED.equals(approvalStatus) && VERIFICATION_VERIFIED.equals(verificationStatus);
    }

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public String getParameterCode() { return parameterCode; }
    public void setParameterCode(String v) { this.parameterCode = v; }
    public String getDomain() { return domain; }
    public void setDomain(String v) { this.domain = v; }
    public String getValueType() { return valueType; }
    public void setValueType(String v) { this.valueType = v; }
    public String getValueText() { return valueText; }
    public void setValueText(String v) { this.valueText = v; }
    public String getUnit() { return unit; }
    public void setUnit(String v) { this.unit = v; }
    public LocalDate getEffectiveStart() { return effectiveStart; }
    public void setEffectiveStart(LocalDate v) { this.effectiveStart = v; }
    public LocalDate getEffectiveEnd() { return effectiveEnd; }
    public void setEffectiveEnd(LocalDate v) { this.effectiveEnd = v; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer v) { this.version = v; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String v) { this.approvalStatus = v; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String v) { this.verificationStatus = v; }
    public String getLegalBasis() { return legalBasis; }
    public void setLegalBasis(String v) { this.legalBasis = v; }
    public String getContentVersion() { return contentVersion; }
    public void setContentVersion(String v) { this.contentVersion = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
}
