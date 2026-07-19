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

@Entity
@Table(name = "cv_claims")
public class ClaimEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pod_id", nullable = false, length = 64)
    private String podId = "national-spine";

    @Column(name = "coverage_id", nullable = false)
    private UUID coverageId;

    @Column(name = "preauth_id")
    private UUID preauthId;

    @Column(name = "facility_id", nullable = false, length = 255)
    private String facilityId;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    @Column(name = "encounter_id", length = 255)
    private String encounterId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "DRAFT";

    // Ruvimbo claims v2 (V017).
    @Column(name = "claim_number", length = 64)
    private String claimNumber;
    @Column(name = "member_cpid", length = 255)
    private String memberCpid;
    @Column(name = "plan_version_id")
    private UUID planVersionId;
    @Column(name = "authorisation_id")
    private UUID authorisationId;
    @Column(name = "referral_id")
    private UUID referralId;
    @Column(name = "allowed_amount", precision = 14, scale = 2)
    private BigDecimal allowedAmount;
    @Column(name = "denied_amount", precision = 14, scale = 2)
    private BigDecimal deniedAmount;
    @Column(name = "patient_responsibility", precision = 14, scale = 2)
    private BigDecimal patientResponsibility;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_codes", columnDefinition = "jsonb")
    private String reasonCodes;
    @Column(name = "ruleset_version", length = 32)
    private String rulesetVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scrub_result", columnDefinition = "jsonb")
    private String scrubResult;
    @Column(name = "replaces_claim_id")
    private UUID replacesClaimId;
    @Column(name = "replaced_by_claim_id")
    private UUID replacedByClaimId;
    @Column(name = "reversed", nullable = false)
    private boolean reversed = false;
    @Column(name = "settlement_ref", length = 128)
    private String settlementRef;
    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "USD";

    @Column(name = "claim_type", nullable = false, length = 32)
    private String claimType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "line_items", nullable = false, columnDefinition = "jsonb")
    private String lineItems;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "approved_amount", precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "adjudication", columnDefinition = "jsonb")
    private String adjudication;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "adjudicated_at")
    private OffsetDateTime adjudicatedAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ClaimEntity() {}

    public ClaimEntity(UUID tenantId, String podId, UUID coverageId, UUID preauthId,
                       String facilityId, String providerId, String encounterId,
                       String claimType, String lineItems, BigDecimal totalAmount) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.podId = podId;
        this.coverageId = coverageId;
        this.preauthId = preauthId;
        this.facilityId = facilityId;
        this.providerId = providerId;
        this.encounterId = encounterId;
        this.claimType = claimType;
        this.lineItems = lineItems;
        this.totalAmount = totalAmount;
        this.status = "SUBMITTED";
        OffsetDateTime now = OffsetDateTime.now();
        this.submittedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Ruvimbo v2 draft factory — starts DRAFT (not SUBMITTED) for the scrub→submit→adjudicate flow. */
    public static ClaimEntity draft(UUID tenantId, String podId, UUID coverageId, String memberCpid,
                                    String facilityId, String providerId, String encounterId,
                                    String claimType, String lineItems, BigDecimal totalAmount) {
        ClaimEntity c = new ClaimEntity();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId;
        c.podId = podId;
        c.coverageId = coverageId;
        c.memberCpid = memberCpid;
        c.facilityId = facilityId;
        c.providerId = providerId;
        c.encounterId = encounterId;
        c.claimType = claimType;
        c.lineItems = lineItems;
        c.totalAmount = totalAmount;
        c.status = "DRAFT";
        c.claimNumber = "CLM-" + c.id.toString().substring(0, 8).toUpperCase();
        OffsetDateTime now = OffsetDateTime.now();
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }

    public void touch() { this.updatedAt = OffsetDateTime.now(); }
    public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public void setSubmittedAt(OffsetDateTime v) { this.submittedAt = v; }
    public void setAdjudicatedAt(OffsetDateTime v) { this.adjudicatedAt = v; }
    public void setApprovedAmount(BigDecimal v) { this.approvedAmount = v; }
    public void setAdjudication(String v) { this.adjudication = v; }
    public String getClaimNumber() { return claimNumber; }
    public String getMemberCpid() { return memberCpid; }
    public void setMemberCpid(String v) { this.memberCpid = v; }
    public UUID getPlanVersionId() { return planVersionId; }
    public void setPlanVersionId(UUID v) { this.planVersionId = v; }
    public UUID getAuthorisationId() { return authorisationId; }
    public void setAuthorisationId(UUID v) { this.authorisationId = v; }
    public UUID getReferralId() { return referralId; }
    public void setReferralId(UUID v) { this.referralId = v; }
    public BigDecimal getAllowedAmount() { return allowedAmount; }
    public void setAllowedAmount(BigDecimal v) { this.allowedAmount = v; }
    public BigDecimal getDeniedAmount() { return deniedAmount; }
    public void setDeniedAmount(BigDecimal v) { this.deniedAmount = v; }
    public BigDecimal getPatientResponsibility() { return patientResponsibility; }
    public void setPatientResponsibility(BigDecimal v) { this.patientResponsibility = v; }
    public String getReasonCodes() { return reasonCodes; }
    public void setReasonCodes(String v) { this.reasonCodes = v; }
    public String getRulesetVersion() { return rulesetVersion; }
    public void setRulesetVersion(String v) { this.rulesetVersion = v; }
    public String getScrubResult() { return scrubResult; }
    public void setScrubResult(String v) { this.scrubResult = v; }
    public UUID getReplacesClaimId() { return replacesClaimId; }
    public void setReplacesClaimId(UUID v) { this.replacesClaimId = v; }
    public UUID getReplacedByClaimId() { return replacedByClaimId; }
    public void setReplacedByClaimId(UUID v) { this.replacedByClaimId = v; }
    public boolean isReversed() { return reversed; }
    public void setReversed(boolean v) { this.reversed = v; }
    public String getSettlementRef() { return settlementRef; }
    public void setSettlementRef(String v) { this.settlementRef = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getPodId() { return podId; }
    public UUID getCoverageId() { return coverageId; }
    public UUID getPreauthId() { return preauthId; }
    public String getFacilityId() { return facilityId; }
    public String getProviderId() { return providerId; }
    public String getEncounterId() { return encounterId; }
    public String getStatus() { return status; }
    public String getClaimType() { return claimType; }
    public String getLineItems() { return lineItems; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public String getAdjudication() { return adjudication; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public OffsetDateTime getAdjudicatedAt() { return adjudicatedAt; }
    public OffsetDateTime getPaidAt() { return paidAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }

    /** Marks claim adjudicated (e.g. from MUSHEX feedback). */
    public void markAdjudicated(BigDecimal approvedAmount, String adjudicationJson) {
        this.status = "ADJUDICATED";
        this.approvedAmount = approvedAmount;
        this.adjudication = adjudicationJson;
        this.adjudicatedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    /** Marks claim paid after insurer settlement. */
    public void markPaid(BigDecimal amountPaid) {
        this.status = "PAID";
        if (amountPaid != null) {
            this.approvedAmount = amountPaid;
        }
        this.paidAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }
}
