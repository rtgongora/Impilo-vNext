package zw.gov.mohcc.impilo.costa.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Route a pending emergency-deferred charge to its settlement: SETTLE | CLAIM | WAIVER.
 * Closes the reconciliation and emits an EMERGENCY_DEFERRED_CHARGE value-event (C8).
 */
public record ReconcileDeferredChargeRequest(
        @JsonProperty("settlement_route")
        @NotBlank(message = "settlement_route is required (SETTLE|CLAIM|WAIVER)")
        String settlementRoute,
        @JsonProperty("settlement_reference") String settlementReference,
        @JsonProperty("reason") String reason
) {}
