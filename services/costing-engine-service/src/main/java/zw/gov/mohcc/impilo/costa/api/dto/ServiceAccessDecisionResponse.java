package zw.gov.mohcc.impilo.costa.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import zw.gov.mohcc.impilo.costa.domain.entity.ServiceAccessDecisionEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.BillingTimingMode;
import zw.gov.mohcc.impilo.costa.domain.enums.ServiceAccessStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceAccessDecisionResponse(
        @JsonProperty("service_access_decision_id") UUID serviceAccessDecisionId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("patient_cpid") String patientCpid,
        @JsonProperty("encounter_id") String encounterId,
        @JsonProperty("facility_id") UUID facilityId,
        @JsonProperty("workspace_id") UUID workspaceId,
        @JsonProperty("requested_service_id") String requestedServiceId,
        @JsonProperty("requested_service_name") String requestedServiceName,
        @JsonProperty("requested_service_category") String requestedServiceCategory,
        @JsonProperty("estimated_cost") BigDecimal estimatedCost,
        @JsonProperty("selected_tariff_list_id") Long selectedTariffListId,
        @JsonProperty("tariffed_price") BigDecimal tariffedPrice,
        @JsonProperty("billing_timing_mode") BillingTimingMode billingTimingMode,
        @JsonProperty("access_status") ServiceAccessStatus accessStatus,
        @JsonProperty("required_deposit_amount") BigDecimal requiredDepositAmount,
        @JsonProperty("required_payment_amount") BigDecimal requiredPaymentAmount,
        @JsonProperty("payer_id") String payerId,
        @JsonProperty("coverage_reference") String coverageReference,
        @JsonProperty("exemption_reference") String exemptionReference,
        @JsonProperty("waiver_reference") String waiverReference,
        @JsonProperty("authorisation_reference") String authorisationReference,
        @JsonProperty("promise_to_pay_reference") String promiseToPayReference,
        @JsonProperty("decision_reason") String decisionReason,
        @JsonProperty("decided_by") String decidedBy,
        @JsonProperty("decision_timestamp") OffsetDateTime decisionTimestamp,
        @JsonProperty("audit_reference") String auditReference
) {
    public static ServiceAccessDecisionResponse fromEntity(ServiceAccessDecisionEntity e) {
        return new ServiceAccessDecisionResponse(
                e.getServiceAccessDecisionId(),
                e.getTenantId(),
                e.getPatientCpid(),
                e.getEncounterId(),
                e.getFacilityId(),
                e.getWorkspaceId(),
                e.getRequestedServiceId(),
                e.getRequestedServiceName(),
                e.getRequestedServiceCategory(),
                e.getEstimatedCost(),
                e.getSelectedTariffListId(),
                e.getTariffedPrice(),
                e.getBillingTimingMode(),
                e.getAccessStatus(),
                e.getRequiredDepositAmount(),
                e.getRequiredPaymentAmount(),
                e.getPayerId(),
                e.getCoverageReference(),
                e.getExemptionReference(),
                e.getWaiverReference(),
                e.getAuthorisationReference(),
                e.getPromiseToPayReference(),
                e.getDecisionReason(),
                e.getDecidedBy(),
                e.getDecisionTimestamp(),
                e.getAuditReference()
        );
    }
}
