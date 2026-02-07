package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for initiating a new stock custody handover.
 *
 * @param storeId the store being handed over (required)
 * @param toActor the identifier of the receiving actor (required)
 */
public record StartHandoverRequest(
        @NotNull UUID storeId,
        @NotBlank String toActor
) {}
