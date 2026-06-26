package zw.gov.mohcc.impilo.costa.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import zw.gov.mohcc.impilo.costa.domain.entity.WaiverEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.WaiverStatus;
import zw.gov.mohcc.impilo.costa.domain.enums.WaiverType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WaiverResponse(
        @JsonProperty("waiver_id") UUID waiverId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("waiver_reference") String waiverReference,
        @JsonProperty("patient_cpid") String patientCpid,
        @JsonProperty("encounter_id") String encounterId,
        @JsonProperty("bill_id") String billId,
        @JsonProperty("charge_id") String chargeId,
        @JsonProperty("deferred_charge_id") UUID deferredChargeId,
        @JsonProperty("waiver_type") WaiverType waiverType,
        @JsonProperty("waived_amount") BigDecimal waivedAmount,
        @JsonProperty("currency") String currency,
        @JsonProperty("reason_category") String reasonCategory,
        @JsonProperty("justification") String justification,
        @JsonProperty("status") WaiverStatus status,
        @JsonProperty("granted_by") String grantedBy,
        @JsonProperty("granted_at") OffsetDateTime grantedAt,
        @JsonProperty("approved_by") String approvedBy,
        @JsonProperty("approved_at") OffsetDateTime approvedAt,
        @JsonProperty("rejected_reason") String rejectedReason,
        @JsonProperty("revoked_by") String revokedBy,
        @JsonProperty("revoked_at") OffsetDateTime revokedAt,
        @JsonProperty("revoke_reason") String revokeReason,
        @JsonProperty("audit_reference") String auditReference,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt
) {
    public static WaiverResponse fromEntity(WaiverEntity e) {
        return new WaiverResponse(
                e.getWaiverId(), e.getTenantId(), e.getWaiverReference(),
                e.getPatientCpid(), e.getEncounterId(), e.getBillId(), e.getChargeId(),
                e.getDeferredChargeId(), e.getWaiverType(), e.getWaivedAmount(), e.getCurrency(),
                e.getReasonCategory(), e.getJustification(), e.getStatus(),
                e.getGrantedBy(), e.getGrantedAt(), e.getApprovedBy(), e.getApprovedAt(),
                e.getRejectedReason(), e.getRevokedBy(), e.getRevokedAt(), e.getRevokeReason(),
                e.getAuditReference(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
