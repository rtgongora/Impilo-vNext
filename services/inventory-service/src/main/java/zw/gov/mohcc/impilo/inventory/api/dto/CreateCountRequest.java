package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating a new stock count session.
 *
 * @param storeId the store to count (required)
 * @param binId   the specific bin to count (optional; null for whole-store count)
 */
public record CreateCountRequest(
        @NotNull UUID storeId,
        UUID binId
) {}
