package zw.gov.mohcc.impilo.costa.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Register a {@code ServiceAccessDecision} (pre-service gate, PoC hold, or policy outcome snapshot).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceAccessDecisionCreateRequest(
        @JsonProperty("access_status") @NotBlank String accessStatus,
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
        @JsonProperty("billing_timing_mode") String billingTimingMode,
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
        @JsonProperty("audit_reference") String auditReference
) {}
