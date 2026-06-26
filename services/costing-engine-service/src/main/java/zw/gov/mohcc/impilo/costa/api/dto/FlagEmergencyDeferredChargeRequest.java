package zw.gov.mohcc.impilo.costa.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Flag a charge that accrued under an EMERGENCY_OVERRIDE access decision as deferred,
 * to be reconciled post-care. Either a service-access-decision id or an explicit
 * provisional person + amount is acceptable; both may be supplied.
 */
public record FlagEmergencyDeferredChargeRequest(
        @JsonProperty("service_access_decision_id") UUID serviceAccessDecisionId,
        @JsonProperty("charge_id") String chargeId,
        @JsonProperty("encounter_id") String encounterId,
        @JsonProperty("facility_id") UUID facilityId,
        @JsonProperty("provisional_person_ref") String provisionalPersonRef,
        @JsonProperty("deferred_amount") BigDecimal deferredAmount,
        @JsonProperty("currency") String currency,
        @JsonProperty("reason") String reason,
        @JsonProperty("audit_reference") String auditReference
) {}
