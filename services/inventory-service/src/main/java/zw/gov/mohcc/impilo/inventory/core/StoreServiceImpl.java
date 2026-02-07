package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.BinType;
import zw.gov.mohcc.impilo.inventory.domain.StoreType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BinEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.StoreEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.BinRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.StoreRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Store and bin management implementation.
 *
 * <p>Manages the physical storage hierarchy within facilities. Stores
 * represent top-level or nested storage locations (warehouses, pharmacies,
 * labs), while bins represent subdivisions within a store for finer-grained
 * stock placement and tracking.</p>
 *
 * <p>Stores can be hierarchical via the {@code parentStoreId} field,
 * allowing warehouse zones, sub-stores, and satellite stores to be modeled
 * as children of a main store.</p>
 */
@Service
public class StoreServiceImpl implements StoreService {

    private static final Logger log = LoggerFactory.getLogger(StoreServiceImpl.class);

    private final StoreRepository storeRepository;
    private final BinRepository binRepository;

    public StoreServiceImpl(StoreRepository storeRepository, BinRepository binRepository) {
        this.storeRepository = storeRepository;
        this.binRepository = binRepository;
    }

    @Override
    @Transactional
    public StoreEntity createStore(UUID facilityId, String name, StoreType type, UUID parentStoreId) {
        TrustContext ctx = TrustContextHolder.require();

        if (facilityId == null) {
            throw new IllegalArgumentException("facilityId is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Store name is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Store type is required");
        }

        // Validate parent store exists if specified
        if (parentStoreId != null) {
            StoreEntity parent = storeRepository.findById(parentStoreId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Parent store not found: " + parentStoreId));
            // Ensure parent belongs to the same facility
            if (!parent.getFacilityId().equals(facilityId)) {
                throw new IllegalArgumentException(
                        "Parent store " + parentStoreId + " belongs to a different facility");
            }
        }

        StoreEntity store = new StoreEntity();
        store.setTenantId(ctx.tenantId());
        store.setFacilityId(facilityId);
        store.setName(name);
        store.setStoreType(type);
        store.setParentStoreId(parentStoreId);

        store = storeRepository.save(store);

        log.info("Store created: storeId={}, facility={}, name={}, type={}, parent={}",
                store.getStoreId(), facilityId, name, type, parentStoreId);
        return store;
    }

    @Override
    @Transactional
    public BinEntity createBin(UUID storeId, String name, BinType type) {
        TrustContext ctx = TrustContextHolder.require();

        if (storeId == null) {
            throw new IllegalArgumentException("storeId is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bin name is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Bin type is required");
        }

        // Verify the store exists
        StoreEntity store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        BinEntity bin = new BinEntity();
        bin.setTenantId(ctx.tenantId());
        bin.setStoreId(storeId);
        bin.setName(name);
        bin.setBinType(type);

        bin = binRepository.save(bin);

        log.info("Bin created: binId={}, store={}, name={}, type={}",
                bin.getBinId(), storeId, name, type);
        return bin;
    }

    @Override
    public List<StoreEntity> getStores(UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();

        if (facilityId == null) {
            throw new IllegalArgumentException("facilityId is required");
        }

        return storeRepository.findByTenantIdAndFacilityId(ctx.tenantId(), facilityId);
    }

    @Override
    public List<BinEntity> getBins(UUID storeId) {
        TrustContextHolder.require(); // ensure trust context exists

        if (storeId == null) {
            throw new IllegalArgumentException("storeId is required");
        }

        return binRepository.findByStoreId(storeId);
    }
}
