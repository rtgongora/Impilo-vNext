package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import zw.gov.mohcc.impilo.inventory.domain.HomeStockSource;

import java.time.LocalDate;

/**
 * Request to add a household stock item.
 */
public record AddHomeStockRequest(
        @NotBlank String clientId,
        String itemCode,
        @NotBlank String itemName,
        @PositiveOrZero int qty,
        String uom,
        String batchNumber,
        LocalDate expiryDate,
        HomeStockSource source,
        String managedByCaregiver,
        String notes
) {}
