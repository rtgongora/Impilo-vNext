package zw.gov.mohcc.impilo.pharmacy.persistence.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.pharmacy.domain.ClarificationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * OF-B15 — a prescriber clarification request raised when a proposed
 * substitution requires approval (§8.10.1 item 1: "any new substitution need →
 * prescriber clarification loop, never unilateral").
 *
 * <p>The dispense episode holds while a request is PENDING; approval applies
 * the substitution exactly once ({@code appliedAt} marks consumption); decline
 * is fail-closed.</p>
 */
@Entity
@Table(name = "rx_clarification_requests")
public class ClarificationRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "clarification_id", nullable = false)
    private UUID clarificationId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "dispense_order_id", nullable = false)
    private UUID dispenseOrderId;

    @Column(name = "dispense_item_id", nullable = false)
    private UUID dispenseItemId;

    @Column(name = "source_drug_code", nullable = false)
    private String sourceDrugCode;

    @Column(name = "target_drug_code", nullable = false)
    private String targetDrugCode;

    @Column(name = "rule_type")
    private String ruleType;

    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    /** §8.10 patient-consent flag: patient informed and consented in plain language. */
    @Column(name = "patient_consent", nullable = false)
    private boolean patientConsent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClarificationStatus status = ClarificationStatus.PENDING;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "prescriber_ref")
    private String prescriberRef;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    /** Set when an APPROVED clarification has been consumed by the substitution. */
    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (requestedAt == null) {
            requestedAt = createdAt;
        }
    }

    // Getters and setters

    public UUID getClarificationId() { return clarificationId; }
    public void setClarificationId(UUID clarificationId) { this.clarificationId = clarificationId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }

    public UUID getDispenseOrderId() { return dispenseOrderId; }
    public void setDispenseOrderId(UUID dispenseOrderId) { this.dispenseOrderId = dispenseOrderId; }

    public UUID getDispenseItemId() { return dispenseItemId; }
    public void setDispenseItemId(UUID dispenseItemId) { this.dispenseItemId = dispenseItemId; }

    public String getSourceDrugCode() { return sourceDrugCode; }
    public void setSourceDrugCode(String sourceDrugCode) { this.sourceDrugCode = sourceDrugCode; }

    public String getTargetDrugCode() { return targetDrugCode; }
    public void setTargetDrugCode(String targetDrugCode) { this.targetDrugCode = targetDrugCode; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isPatientConsent() { return patientConsent; }
    public void setPatientConsent(boolean patientConsent) { this.patientConsent = patientConsent; }

    public ClarificationStatus getStatus() { return status; }
    public void setStatus(ClarificationStatus status) { this.status = status; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }

    public String getPrescriberRef() { return prescriberRef; }
    public void setPrescriberRef(String prescriberRef) { this.prescriberRef = prescriberRef; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public OffsetDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(OffsetDateTime appliedAt) { this.appliedAt = appliedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
