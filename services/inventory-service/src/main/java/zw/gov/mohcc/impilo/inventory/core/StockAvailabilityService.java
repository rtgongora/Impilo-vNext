package zw.gov.mohcc.impilo.inventory.core;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dura stock-aware availability service for PCT and other clinical workflows.
 *
 * <p>Answers the questions PCT must ask before a stock-dependent action: is the
 * commodity available here, is there usable (non-expired, non-quarantined,
 * non-recalled) stock, what is the nearest expiry, and — when stocked-out — is
 * there an approved substitute with stock.</p>
 *
 * <p>All operations are tenant-scoped via the trust context.</p>
 */
public interface StockAvailabilityService {

    /**
     * Assess availability of a commodity at a store.
     */
    AvailabilityResult availability(UUID facilityId, UUID storeId, String itemCode);

    /**
     * Summary of a usable batch.
     */
    record UsableBatch(UUID batchLotId, String batchNumber, LocalDate expiryDate) {}

    /**
     * An approved substitute commodity that currently has available stock.
     */
    record SubstituteOption(String itemCode, String name, int available) {}

    /**
     * Availability decision payload.
     *
     * @param itemCode      the requested commodity
     * @param available     available quantity (on-hand − active reservations)
     * @param onHand        on-hand quantity
     * @param reserved      quantity held by active reservations
     * @param stockOut      true when available ≤ 0
     * @param usableBatches usable (active, non-expired) batches, FEFO order
     * @param nearestExpiry earliest expiry among usable batches ({@code null} if none)
     * @param substitutes   approved substitutes with stock (only populated when stocked-out)
     */
    record AvailabilityResult(
            String itemCode,
            int available,
            int onHand,
            int reserved,
            boolean stockOut,
            List<UsableBatch> usableBatches,
            LocalDate nearestExpiry,
            List<SubstituteOption> substitutes
    ) {}
}
