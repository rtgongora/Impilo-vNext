package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO representing an inventory item from the catalog.
 *
 * @param itemId      the item primary key
 * @param itemCode    the unique item code
 * @param name        the display name
 * @param uom         the unit of measure
 * @param barcode     the barcode (GTIN or internal)
 * @param category    the item category
 * @param controlled  whether the item is a controlled substance
 * @param ziboRefs    JSON string of ZIBO terminology references
 * @param active      whether the item is active in the catalog
 * @param createdAt   creation timestamp
 * @param updatedAt   last update timestamp
 */
public record ItemDto(
        UUID itemId,
        String itemCode,
        String name,
        String uom,
        String barcode,
        String category,
        boolean controlled,
        String ziboRefs,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /**
     * Map an ItemEntity to an ItemDto.
     *
     * @param entity the item entity
     * @return the DTO representation
     */
    public static ItemDto from(ItemEntity entity) {
        return new ItemDto(
                entity.getItemId(),
                entity.getItemCode(),
                entity.getName(),
                entity.getUom(),
                entity.getBarcode(),
                entity.getCategory(),
                entity.isControlled(),
                entity.getZiboRefs(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
