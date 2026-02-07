package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ItemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Item catalog management implementation.
 *
 * <p>Manages the inventory item registry, providing CRUD operations,
 * barcode-based lookups, and bulk import for integration with external
 * systems like ZIBO (terminology service). Items are scoped to tenants.</p>
 */
@Service
public class ItemServiceImpl implements ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemServiceImpl.class);

    private final ItemRepository itemRepository;

    public ItemServiceImpl(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    @Transactional
    public ItemEntity createItem(String itemCode, String name, String uom, String barcode,
                                   String category, boolean controlled, String ziboRefsJson) {
        TrustContext ctx = TrustContextHolder.require();

        if (itemCode == null || itemCode.isBlank()) {
            throw new IllegalArgumentException("itemCode is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }

        // Check for duplicate item code
        Optional<ItemEntity> existing = itemRepository.findByItemCode(itemCode);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Item already exists with code: " + itemCode);
        }

        ItemEntity item = new ItemEntity();
        item.setTenantId(ctx.tenantId());
        item.setItemCode(itemCode);
        item.setName(name);
        item.setUom(uom);
        item.setBarcode(barcode);
        item.setCategory(category);
        item.setControlled(controlled);
        item.setZiboRefs(ziboRefsJson);

        item = itemRepository.save(item);

        log.info("Item created: itemCode={}, name={}, uom={}, controlled={}",
                itemCode, name, uom, controlled);
        return item;
    }

    @Override
    public ItemEntity getItem(String itemCode) {
        TrustContextHolder.require(); // ensure trust context exists

        if (itemCode == null || itemCode.isBlank()) {
            throw new IllegalArgumentException("itemCode is required");
        }

        return itemRepository.findByItemCode(itemCode)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemCode));
    }

    /**
     * {@inheritDoc}
     *
     * <p>For each item in the import list:
     * <ul>
     *   <li>If an item with the same code already exists, its fields are updated</li>
     *   <li>If no item exists with that code, a new item is created</li>
     * </ul>
     *
     * <p>All operations are performed within a single transaction for atomicity.</p>
     */
    @Override
    @Transactional
    public List<ItemEntity> importItems(List<ItemImportData> items) {
        TrustContext ctx = TrustContextHolder.require();

        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<ItemEntity> results = new ArrayList<>(items.size());
        int created = 0;
        int updated = 0;

        for (ItemImportData importData : items) {
            if (importData.itemCode() == null || importData.itemCode().isBlank()) {
                log.warn("Skipping import item with null/blank itemCode");
                continue;
            }

            Optional<ItemEntity> existing = itemRepository.findByItemCode(importData.itemCode());

            ItemEntity item;
            if (existing.isPresent()) {
                // Update existing item
                item = existing.get();
                item.setName(importData.name());
                item.setUom(importData.uom());
                item.setBarcode(importData.barcode());
                item.setCategory(importData.category());
                item.setControlled(importData.controlled());
                updated++;
            } else {
                // Create new item
                item = new ItemEntity();
                item.setTenantId(ctx.tenantId());
                item.setItemCode(importData.itemCode());
                item.setName(importData.name());
                item.setUom(importData.uom());
                item.setBarcode(importData.barcode());
                item.setCategory(importData.category());
                item.setControlled(importData.controlled());
                created++;
            }

            item = itemRepository.save(item);
            results.add(item);
        }

        log.info("Item import complete: total={}, created={}, updated={}",
                items.size(), created, updated);
        return results;
    }

    @Override
    public ItemEntity lookupByBarcode(String barcode) {
        TrustContextHolder.require(); // ensure trust context exists

        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("barcode is required");
        }

        return itemRepository.findFirstByBarcode(barcode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No item found for barcode: " + barcode));
    }
}
