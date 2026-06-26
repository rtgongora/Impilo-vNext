package zw.gov.mohcc.impilo.costa.domain.entity;

import jakarta.persistence.*;
import zw.gov.mohcc.impilo.costa.domain.enums.ReconciliationState;
import zw.gov.mohcc.impilo.costa.domain.enums.SettlementRoute;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Reconciliation ledger row for a charge accrued under {@code EMERGENCY_OVERRIDE}
 * (Journey Law 1 — emergency care is never blocked; payment/identity are deferred).
 *
 * <p>This is the workflow ledger over two systems-of-record: the gate decision
 * ({@code costa_service_access_decisions}) and the concrete charge
 * ({@code costa_charge_records}). It records the provisional→confirmed person link
 * without overwriting VITO, and routes each deferred charge to settle/claim/waiver.
 */
@Entity
@Table(name = "costa_emergency_deferred_charges")
public class EmergencyDeferredChargeEntity {

    @Id
    @Column(name = "deferred_charge_id")
    private UUID deferredChargeId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "service_access_decision_id")
    private UUID serviceAccessDecisionId;

    @Column(name = "charge_id", length = 26)
    private String chargeId;

    @Column(name = "encounter_id", length = 80)
    private String encounterId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "provisional_person_ref", length = 128)
    private String provisionalPersonRef;

    @Column(name = "confirmed_person_ref", length = 128)
    private String confirmedPersonRef;

    @Column(name = "identity_reconciled", nullable = false)
    private boolean identityReconciled = false;

    @Column(name = "identity_reconciled_at")
    private OffsetDateTime identityReconciledAt;

    @Column(name = "deferred_amount", precision = 14, scale = 2)
    private BigDecimal deferredAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_state", nullable = false, length = 20)
    private ReconciliationState reconciliationState = ReconciliationState.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_route", length = 20)
    private SettlementRoute settlementRoute;

    @Column(name = "settlement_reference", length = 200)
    private String settlementReference;

    @Column(name = "reconciliation_reason", columnDefinition = "TEXT")
    private String reconciliationReason;

    @Column(name = "flagged_by", length = 120)
    private String flaggedBy;

    @Column(name = "reconciled_by", length = 120)
    private String reconciledBy;

    @Column(name = "audit_reference", length = 120)
    private String auditReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "reconciled_at")
    private OffsetDateTime reconciledAt;

    @PrePersist
    void prePersist() {
        if (deferredChargeId == null) {
            deferredChargeId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getDeferredChargeId() { return deferredChargeId; }
    public void setDeferredChargeId(UUID deferredChargeId) { this.deferredChargeId = deferredChargeId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getServiceAccessDecisionId() { return serviceAccessDecisionId; }
    public void setServiceAccessDecisionId(UUID serviceAccessDecisionId) { this.serviceAccessDecisionId = serviceAccessDecisionId; }
    public String getChargeId() { return chargeId; }
    public void setChargeId(String chargeId) { this.chargeId = chargeId; }
    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getProvisionalPersonRef() { return provisionalPersonRef; }
    public void setProvisionalPersonRef(String provisionalPersonRef) { this.provisionalPersonRef = provisionalPersonRef; }
    public String getConfirmedPersonRef() { return confirmedPersonRef; }
    public void setConfirmedPersonRef(String confirmedPersonRef) { this.confirmedPersonRef = confirmedPersonRef; }
    public boolean isIdentityReconciled() { return identityReconciled; }
    public void setIdentityReconciled(boolean identityReconciled) { this.identityReconciled = identityReconciled; }
    public OffsetDateTime getIdentityReconciledAt() { return identityReconciledAt; }
    public void setIdentityReconciledAt(OffsetDateTime identityReconciledAt) { this.identityReconciledAt = identityReconciledAt; }
    public BigDecimal getDeferredAmount() { return deferredAmount; }
    public void setDeferredAmount(BigDecimal deferredAmount) { this.deferredAmount = deferredAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public ReconciliationState getReconciliationState() { return reconciliationState; }
    public void setReconciliationState(ReconciliationState reconciliationState) { this.reconciliationState = reconciliationState; }
    public SettlementRoute getSettlementRoute() { return settlementRoute; }
    public void setSettlementRoute(SettlementRoute settlementRoute) { this.settlementRoute = settlementRoute; }
    public String getSettlementReference() { return settlementReference; }
    public void setSettlementReference(String settlementReference) { this.settlementReference = settlementReference; }
    public String getReconciliationReason() { return reconciliationReason; }
    public void setReconciliationReason(String reconciliationReason) { this.reconciliationReason = reconciliationReason; }
    public String getFlaggedBy() { return flaggedBy; }
    public void setFlaggedBy(String flaggedBy) { this.flaggedBy = flaggedBy; }
    public String getReconciledBy() { return reconciledBy; }
    public void setReconciledBy(String reconciledBy) { this.reconciledBy = reconciledBy; }
    public String getAuditReference() { return auditReference; }
    public void setAuditReference(String auditReference) { this.auditReference = auditReference; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(OffsetDateTime reconciledAt) { this.reconciledAt = reconciledAt; }
}
