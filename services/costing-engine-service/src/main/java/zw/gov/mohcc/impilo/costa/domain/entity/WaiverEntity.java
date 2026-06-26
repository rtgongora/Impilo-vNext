package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.costa.domain.enums.WaiverStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.WaiverType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A discretionary fee waiver — a one-off decision to waive part or all of a specific
 * charge/bill, with an approval lifecycle. Distinct from {@link ExemptionRuleEntity}
 * (a standing catalog rule applied automatically by the ExemptionEngine).
 */
@Entity
@Table(name = "costa_waivers")
public class WaiverEntity {

    @Id
    @Column(name = "waiver_id")
    private UUID waiverId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "waiver_reference", nullable = false, length = 40)
    private String waiverReference;

    @Column(name = "patient_cpid", length = 80)
    private String patientCpid;

    @Column(name = "encounter_id", length = 80)
    private String encounterId;

    @Column(name = "bill_id", length = 26)
    private String billId;

    @Column(name = "charge_id", length = 26)
    private String chargeId;

    @Column(name = "deferred_charge_id")
    private UUID deferredChargeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "waiver_type", nullable = false, length = 20)
    private WaiverType waiverType;

    @Column(name = "waived_amount", precision = 14, scale = 2)
    private BigDecimal waivedAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "reason_category", length = 40)
    private String reasonCategory;

    @Column(name = "justification", nullable = false, columnDefinition = "TEXT")
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WaiverStatus status = WaiverStatus.GRANTED;

    @Column(name = "granted_by", nullable = false, length = 120)
    private String grantedBy;

    @Column(name = "granted_at", nullable = false)
    private OffsetDateTime grantedAt;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    private String rejectedReason;

    @Column(name = "revoked_by", length = 120)
    private String revokedBy;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoke_reason", columnDefinition = "TEXT")
    private String revokeReason;

    @Column(name = "audit_reference", length = 120)
    private String auditReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (waiverId == null) {
            waiverId = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (grantedAt == null) grantedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getWaiverId() { return waiverId; }
    public void setWaiverId(UUID waiverId) { this.waiverId = waiverId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getWaiverReference() { return waiverReference; }
    public void setWaiverReference(String waiverReference) { this.waiverReference = waiverReference; }
    public String getPatientCpid() { return patientCpid; }
    public void setPatientCpid(String patientCpid) { this.patientCpid = patientCpid; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }
    public String getBillId() { return billId; }
    public void setBillId(String billId) { this.billId = billId; }
    public String getChargeId() { return chargeId; }
    public void setChargeId(String chargeId) { this.chargeId = chargeId; }
    public UUID getDeferredChargeId() { return deferredChargeId; }
    public void setDeferredChargeId(UUID deferredChargeId) { this.deferredChargeId = deferredChargeId; }
    public WaiverType getWaiverType() { return waiverType; }
    public void setWaiverType(WaiverType waiverType) { this.waiverType = waiverType; }
    public BigDecimal getWaivedAmount() { return waivedAmount; }
    public void setWaivedAmount(BigDecimal waivedAmount) { this.waivedAmount = waivedAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getReasonCategory() { return reasonCategory; }
    public void setReasonCategory(String reasonCategory) { this.reasonCategory = reasonCategory; }
    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }
    public WaiverStatus getStatus() { return status; }
    public void setStatus(WaiverStatus status) { this.status = status; }
    public String getGrantedBy() { return grantedBy; }
    public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
    public OffsetDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(OffsetDateTime grantedAt) { this.grantedAt = grantedAt; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getRejectedReason() { return rejectedReason; }
    public void setRejectedReason(String rejectedReason) { this.rejectedReason = rejectedReason; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
    public String getAuditReference() { return auditReference; }
    public void setAuditReference(String auditReference) { this.auditReference = auditReference; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
