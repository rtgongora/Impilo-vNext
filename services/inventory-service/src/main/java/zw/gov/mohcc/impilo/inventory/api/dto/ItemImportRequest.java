package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request DTO for bulk item import.
 *
 * @param items the list of items to import (must not be empty)
 */
public record ItemImportRequest(
        @NotEmpty @Valid List<ItemImportData> items
) {

    /**
     * Single item within a bulk import payload.
     *
     * @param itemCode   the unique item code
     * @param name       the display name
     * @param uom        the unit of measure
     * @param barcode    the barcode (GTIN or internal)
     * @param category   the item category
     * @param controlled whether the item is a controlled substance
     */
    public record ItemImportData(
            String itemCode,
            String name,
            String uom,
            String barcode,
            String category,
            boolean controlled
    ) {}
}
