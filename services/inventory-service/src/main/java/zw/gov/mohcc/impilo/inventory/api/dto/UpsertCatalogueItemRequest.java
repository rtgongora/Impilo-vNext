package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Request to create/update a supplier catalogue item. */
public record UpsertCatalogueItemRequest(
        String itemCode,
        @NotBlank String itemName,
        String packSize,
        BigDecimal unitPrice,
        String currency,
        @PositiveOrZero int availableQty
) {}
