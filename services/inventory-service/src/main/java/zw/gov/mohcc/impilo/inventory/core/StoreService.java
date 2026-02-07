package zw.gov.mohcc.impilo.inventory.core;

import zw.gov.mohcc.impilo.inventory.domain.BinType;
import zw.gov.mohcc.impilo.inventory.domain.StoreType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BinEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.StoreEntity;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing stores and bins within facilities.
 *
 * <p>Stores represent physical or logical storage locations within a facility.
 * Bins are subdivisions within a store for finer-grained stock tracking.
 * Stores can be hierarchical (parent-child) for modeling warehouse zones.</p>
 */
public interface StoreService {

    /**
     * Create a new store within a facility.
     *
     * @param facilityId    the facility identifier
     * @param name          the store name
     * @param type          the store type (e.g. MAIN, SUB, PHARMACY, LAB)
     * @param parentStoreId the parent store identifier (nullable for top-level stores)
     * @return the created store entity
     */
    StoreEntity createStore(UUID facilityId, String name, StoreType type, UUID parentStoreId);

    /**
     * Create a new bin within a store.
     *
     * @param storeId the store identifier
     * @param name    the bin name
     * @param type    the bin type (e.g. BULK, PICKING, QUARANTINE)
     * @return the created bin entity
     */
    BinEntity createBin(UUID storeId, String name, BinType type);

    /**
     * List all stores for a facility.
     *
     * @param facilityId the facility identifier
     * @return list of stores
     */
    List<StoreEntity> getStores(UUID facilityId);

    /**
     * List all bins within a store.
     *
     * @param storeId the store identifier
     * @return list of bins
     */
    List<BinEntity> getBins(UUID storeId);
}
