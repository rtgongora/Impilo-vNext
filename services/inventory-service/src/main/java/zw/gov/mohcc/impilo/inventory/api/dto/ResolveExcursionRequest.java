package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.inventory.domain.ExcursionResolution;

/**
 * Request to resolve a cold-chain excursion.
 */
public record ResolveExcursionRequest(
        @NotNull ExcursionResolution resolution,
        String notes
) {}
