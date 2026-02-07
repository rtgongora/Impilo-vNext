package zw.gov.mohcc.impilo.pharmacy.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Individual wastage item data within a {@link WastageRequest}.
 *
 * @param itemId    the dispense item ID
 * @param qtyWasted the quantity being wasted
 * @param reason    the reason for the wastage
 */
public record WastageItemData(
        @NotNull UUID itemId,
        @Min(1) int qtyWasted,
        String reason
) {}
