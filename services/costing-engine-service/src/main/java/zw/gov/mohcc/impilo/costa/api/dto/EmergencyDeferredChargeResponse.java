package zw.gov.mohcc.impilo.costa.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import zw.gov.mohcc.impilo.costa.domain.entity.EmergencyDeferredChargeEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.ReconciliationState;
import zw.gov.mohcc.impilo.costa.domain.enums.SettlementRoute;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EmergencyDeferredChargeResponse(
        @JsonProperty("deferred_charge_id") UUID deferredChargeId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("service_access_decision_id") UUID serviceAccessDecisionId,
        @JsonProperty("charge_id") String chargeId,
        @JsonProperty("encounter_id") String encounterId,
        @JsonProperty("facility_id") UUID facilityId,
        @JsonProperty("provisional_person_ref") String provisionalPersonRef,
        @JsonProperty("confirmed_person_ref") String confirmedPersonRef,
        @JsonProperty("identity_reconciled") boolean identityReconciled,
        @JsonProperty("identity_reconciled_at") OffsetDateTime identityReconciledAt,
        @JsonProperty("deferred_amount") BigDecimal deferredAmount,
        @JsonProperty("currency") String currency,
        @JsonProperty("reconciliation_state") ReconciliationState reconciliationState,
        @JsonProperty("settlement_route") SettlementRoute settlementRoute,
        @JsonProperty("settlement_reference") String settlementReference,
        @JsonProperty("reconciliation_reason") String reconciliationReason,
        @JsonProperty("flagged_by") String flaggedBy,
        @JsonProperty("reconciled_by") String reconciledBy,
        @JsonProperty("audit_reference") String auditReference,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("reconciled_at") OffsetDateTime reconciledAt
) {
    public static EmergencyDeferredChargeResponse fromEntity(EmergencyDeferredChargeEntity e) {
        return new EmergencyDeferredChargeResponse(
                e.getDeferredChargeId(),
                e.getTenantId(),
                e.getServiceAccessDecisionId(),
                e.getChargeId(),
                e.getEncounterId(),
                e.getFacilityId(),
                e.getProvisionalPersonRef(),
                e.getConfirmedPersonRef(),
                e.isIdentityReconciled(),
                e.getIdentityReconciledAt(),
                e.getDeferredAmount(),
                e.getCurrency(),
                e.getReconciliationState(),
                e.getSettlementRoute(),
                e.getSettlementReference(),
                e.getReconciliationReason(),
                e.getFlaggedBy(),
                e.getReconciledBy(),
                e.getAuditReference(),
                e.getCreatedAt(),
                e.getReconciledAt()
        );
    }
}
