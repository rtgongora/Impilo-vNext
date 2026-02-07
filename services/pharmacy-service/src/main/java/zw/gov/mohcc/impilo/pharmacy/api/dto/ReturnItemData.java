package zw.gov.mohcc.impilo.pharmacy.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Individual return item data within a {@link ReturnRequest}.
 *
 * @param itemId      the dispense item ID
 * @param qtyReturned the quantity being returned
 * @param reason      the reason for the return
 */
public record ReturnItemData(
        @NotNull UUID itemId,
        @Min(1) int qtyReturned,
        String reason
) {}
