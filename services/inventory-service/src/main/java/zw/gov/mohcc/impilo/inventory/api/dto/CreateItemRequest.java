package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new inventory item in the catalog.
 *
 * @param itemCode     the unique item code (required)
 * @param name         the display name (required)
 * @param uom          the unit of measure (required)
 * @param barcode      the barcode (GTIN or internal)
 * @param category     the item category
 * @param controlled   whether the item is a controlled substance
 * @param ziboRefsJson JSON string of ZIBO terminology references
 */
public record CreateItemRequest(
        @NotBlank String itemCode,
        @NotBlank String name,
        @NotBlank String uom,
        String barcode,
        String category,
        boolean controlled,
        String ziboRefsJson
) {}
