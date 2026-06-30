package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * PCT request to record consumption of stock during a clinical action
 * (dispense, administration, procedure/theatre, lab test, ward use). Creates a
 * Dura ledger ISSUE event and optionally fulfils a prior reservation.
 */
public record PctConsumeRequest(
        @NotNull UUID facilityId,
        @NotNull UUID storeId,
        @NotBlank String itemCode,
        @Positive int qty,
        @NotBlank String refType,
        @NotBlank String refId,
        UUID reservationId
) {}
