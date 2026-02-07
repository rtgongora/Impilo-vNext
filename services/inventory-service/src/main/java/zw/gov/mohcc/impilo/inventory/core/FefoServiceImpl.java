package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BinEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.OnHandEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.BinRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.OnHandRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * First-Expiry-First-Out (FEFO) picking engine implementation.
 *
 * <p>Queries on-hand stock for the requested item, filters to rows with
 * positive quantity, sorts by ascending expiry (nulls last), and allocates
 * from each batch until the requested quantity is satisfied.</p>
 *
 * <p>Each suggestion includes the bin identifier and name so that the
 * picking operator knows exactly where to retrieve the stock.</p>
 */
@Service
public class FefoServiceImpl implements FefoService {

    private static final Logger log = LoggerFactory.getLogger(FefoServiceImpl.class);

    private final OnHandRepository onHandRepository;
    private final BinRepository binRepository;

    public FefoServiceImpl(OnHandRepository onHandRepository, BinRepository binRepository) {
        this.onHandRepository = onHandRepository;
        this.binRepository = binRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Fetch all on-hand rows for the item at the facility/store with qty > 0</li>
     *   <li>Sort by expiry ascending (null expiry treated as furthest future)</li>
     *   <li>Iterate through sorted rows, allocating from each until qtyNeeded is met</li>
     *   <li>Look up bin names for included suggestions</li>
     * </ol>
     */
    @Override
    public List<FefoSuggestion> suggestPick(UUID facilityId, UUID storeId,
                                              String itemCode, int qtyNeeded) {
        if (qtyNeeded <= 0) {
            log.warn("suggestPick called with non-positive qtyNeeded={}", qtyNeeded);
            return List.of();
        }

        // Fetch on-hand rows with positive stock for this item
        List<OnHandEntity> onHandRows = onHandRepository
                .findByFacilityIdAndStoreIdAndItemCodeAndQtyOnHandGreaterThan(
                        facilityId, storeId, itemCode, 0);

        if (onHandRows.isEmpty()) {
            log.debug("No available stock for FEFO: facility={}, store={}, item={}",
                    facilityId, storeId, itemCode);
            return List.of();
        }

        // Sort by expiry ascending, nulls last (null expiry = no expiry = last pick priority)
        onHandRows.sort(Comparator.comparing(
                OnHandEntity::getExpiry,
                Comparator.nullsLast(Comparator.naturalOrder())));

        // Collect unique bin IDs for batch lookup
        Set<UUID> binIds = onHandRows.stream()
                .map(OnHandEntity::getBinId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> binNameMap = loadBinNames(binIds);

        // Allocate from earliest expiry first
        List<FefoSuggestion> suggestions = new ArrayList<>();
        int remaining = qtyNeeded;

        for (OnHandEntity row : onHandRows) {
            if (remaining <= 0) {
                break;
            }

            // Skip expired batches
            if (row.getExpiry() != null && row.getExpiry().isBefore(LocalDate.now())) {
                log.debug("Skipping expired batch: item={}, batch={}, expiry={}",
                        itemCode, row.getBatch(), row.getExpiry());
                continue;
            }

            int available = row.getQtyOnHand();
            int toPick = Math.min(remaining, available);

            String binName = row.getBinId() != null
                    ? binNameMap.getOrDefault(row.getBinId(), "Unknown")
                    : null;

            suggestions.add(new FefoSuggestion(
                    row.getBatch(),
                    row.getExpiry(),
                    toPick,
                    row.getBinId(),
                    binName
            ));

            remaining -= toPick;
        }

        log.info("FEFO suggestion: facility={}, store={}, item={}, qtyNeeded={}, " +
                        "suggestionsCount={}, qtyAllocated={}, shortfall={}",
                facilityId, storeId, itemCode, qtyNeeded,
                suggestions.size(), qtyNeeded - remaining, Math.max(0, remaining));

        return suggestions;
    }

    /**
     * Batch-load bin names for the given bin IDs.
     *
     * @param binIds the set of bin IDs to look up
     * @return map of binId to bin name
     */
    private Map<UUID, String> loadBinNames(Set<UUID> binIds) {
        if (binIds.isEmpty()) {
            return Map.of();
        }

        return binIds.stream()
                .map(binRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(BinEntity::getBinId, BinEntity::getName,
                        (a, b) -> a));
    }
}
