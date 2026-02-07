package zw.gov.mohcc.impilo.inventory.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.inventory.persistence.entity.OnHandEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service providing queries over the current on-hand stock projection.
 *
 * <p>On-hand rows are maintained by the {@link LedgerService} as a denormalized
 * projection of the immutable ledger. This service provides read-only views
 * for dashboards, alerts, and operational queries.</p>
 */
public interface OnHandService {

    /**
     * Query on-hand positions with optional filters.
     *
     * @param facilityId the facility (required)
     * @param storeId    the store (optional, may be null)
     * @param binId      the bin (optional, may be null)
     * @param itemCode   the item code (optional, may be null)
     * @param pageable   pagination and sorting parameters
     * @return a page of matching on-hand rows
     */
    Page<OnHandEntity> getOnHand(UUID facilityId, UUID storeId, UUID binId, String itemCode, Pageable pageable);

    /**
     * Find on-hand rows expiring within the given number of days.
     *
     * @param facilityId    the facility identifier
     * @param daysThreshold number of days from today
     * @return list of on-hand rows with expiry within the threshold
     */
    List<OnHandEntity> getNearExpiry(UUID facilityId, int daysThreshold);

    /**
     * Find on-hand rows where the quantity has reached zero (stockouts).
     *
     * @param facilityId the facility identifier
     * @return list of on-hand rows with zero or negative stock
     */
    List<OnHandEntity> getStockouts(UUID facilityId);

    /**
     * Get the exact on-hand position for a specific item/batch/expiry combination.
     *
     * @param facilityId the facility identifier
     * @param storeId    the store identifier
     * @param itemCode   the item code
     * @param batch      the batch number
     * @param expiry     the expiry date
     * @return the on-hand entity, or null if no position exists
     */
    OnHandEntity getPosition(UUID facilityId, UUID storeId, String itemCode, String batch, LocalDate expiry);
}
