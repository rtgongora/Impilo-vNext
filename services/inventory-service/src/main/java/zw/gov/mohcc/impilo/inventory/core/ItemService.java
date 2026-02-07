package zw.gov.mohcc.impilo.inventory.core;

import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;

import java.util.List;

/**
 * Service for managing the inventory item catalog.
 *
 * <p>Provides CRUD operations for items, barcode-based lookup,
 * and bulk import for integration with external systems like ZIBO
 * (terminology service).</p>
 */
public interface ItemService {

    /**
     * Create a new item in the catalog.
     *
     * @param itemCode    the unique item code
     * @param name        the display name
     * @param uom         the unit of measure
     * @param barcode     the barcode (GTIN or internal)
     * @param category    the item category
     * @param controlled  whether the item is a controlled substance
     * @param ziboRefsJson JSON string of ZIBO terminology references
     * @return the created item entity
     */
    ItemEntity createItem(String itemCode, String name, String uom, String barcode,
                           String category, boolean controlled, String ziboRefsJson);

    /**
     * Retrieve an item by its item code.
     *
     * @param itemCode the item code
     * @return the item entity
     * @throws IllegalArgumentException if item not found
     */
    ItemEntity getItem(String itemCode);

    /**
     * Bulk create or update items from an import source.
     *
     * <p>For each item: if an item with the same code exists for the tenant,
     * it is updated; otherwise a new item is created.</p>
     *
     * @param items the list of items to import
     * @return the list of created/updated item entities
     */
    List<ItemEntity> importItems(List<ItemImportData> items);

    /**
     * Look up an item by its barcode.
     *
     * @param barcode the barcode to search for
     * @return the matching item entity
     * @throws IllegalArgumentException if no item found for the barcode
     */
    ItemEntity lookupByBarcode(String barcode);

    /**
     * Data carrier for bulk item import.
     */
    record ItemImportData(
            String itemCode,
            String name,
            String uom,
            String barcode,
            String category,
            boolean controlled
    ) {}
}
