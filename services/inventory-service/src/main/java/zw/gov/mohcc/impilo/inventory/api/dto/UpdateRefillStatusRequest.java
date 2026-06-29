package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.inventory.domain.RefillStatus;

/**
 * Request to update a refill request's status.
 */
public record UpdateRefillStatusRequest(
        @NotNull RefillStatus status,
        String notes
) {}
