package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request to update a household stock item's held quantity.
 */
public record UpdateHomeStockQuantityRequest(
        @PositiveOrZero int qty
) {}
