package zw.gov.mohcc.impilo.inventory.core;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * First-Expiry-First-Out (FEFO) picking engine.
 *
 * <p>Suggests optimal batch picks by selecting from on-hand stock in
 * ascending expiry order, ensuring the earliest-expiring batches are
 * consumed first to minimize wastage.</p>
 */
public interface FefoService {

    /**
     * Generate FEFO pick suggestions for a requested quantity of an item.
     *
     * <p>Returns a list of batch/bin combinations sorted by earliest expiry,
     * with quantities allocated from each batch until the requested quantity
     * is met. If total available stock is less than the requested quantity,
     * the suggestions will cover only the available amount.</p>
     *
     * @param facilityId the facility identifier
     * @param storeId    the store within the facility
     * @param itemCode   the item code to pick
     * @param qtyNeeded  the total quantity needed
     * @return ordered list of pick suggestions
     */
    List<FefoSuggestion> suggestPick(UUID facilityId, UUID storeId, String itemCode, int qtyNeeded);

    /**
     * Data carrier for a single FEFO pick suggestion.
     */
    record FefoSuggestion(
            String batch,
            LocalDate expiry,
            int qtyToPick,
            UUID binId,
            String binName
    ) {}
}
