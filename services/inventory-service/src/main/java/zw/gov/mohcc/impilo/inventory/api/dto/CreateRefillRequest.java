package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Request to create a client refill request.
 */
public record CreateRefillRequest(
        @NotBlank String clientId,
        String itemCode,
        @NotBlank String itemName,
        @Positive int qtyRequested,
        UUID preferredFacilityId,
        String notes
) {}
