package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for FEFO (First-Expiry-First-Out) pick suggestion.
 *
 * @param facilityId the facility identifier
 * @param storeId    the store identifier
 * @param itemCode   the item code to pick
 * @param qtyNeeded  the total quantity needed (must be positive)
 */
public record FefoSuggestRequest(
        @NotNull UUID facilityId,
        @NotNull UUID storeId,
        @NotBlank String itemCode,
        @Min(1) int qtyNeeded
) {}
