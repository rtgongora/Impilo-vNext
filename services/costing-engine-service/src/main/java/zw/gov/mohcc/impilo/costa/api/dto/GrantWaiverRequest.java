package zw.gov.mohcc.impilo.costa.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

/** Grant a discretionary waiver against a specific charge / bill / deferred charge. */
public record GrantWaiverRequest(
        @JsonProperty("patient_cpid") String patientCpid,
        @JsonProperty("encounter_id") String encounterId,
        @JsonProperty("bill_id") String billId,
        @JsonProperty("charge_id") String chargeId,
        @JsonProperty("deferred_charge_id") UUID deferredChargeId,
        @JsonProperty("waiver_type")
        @NotBlank(message = "waiver_type is required (FULL|PARTIAL)")
        String waiverType,
        @JsonProperty("waived_amount") BigDecimal waivedAmount,
        @JsonProperty("currency") String currency,
        @JsonProperty("reason_category") String reasonCategory,
        @JsonProperty("justification")
        @NotBlank(message = "justification is required")
        String justification,
        @JsonProperty("audit_reference") String auditReference
) {}
