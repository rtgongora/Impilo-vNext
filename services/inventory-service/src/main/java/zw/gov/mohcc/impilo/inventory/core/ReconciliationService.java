package zw.gov.mohcc.impilo.inventory.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ReconcileQueueEntity;

import java.util.UUID;

/**
 * Service managing stock reconciliation between internal records
 * and external supply-chain systems.
 *
 * <p>Provides paginated views of pending reconciliation entries
 * and resolution workflows for operators. Confidence scores indicate
 * how closely internal and external quantities match.</p>
 */
public interface ReconciliationService {

    /**
     * Get pending reconciliation entries for the current tenant.
     *
     * @param pageable pagination and sorting parameters
     * @return a page of pending reconciliation entries
     */
    Page<ReconcileQueueEntity> getPending(Pageable pageable);

    /**
     * Resolve a reconciliation entry, marking it as RESOLVED.
     *
     * @param recId the reconciliation entry identifier
     * @param notes operational notes explaining the resolution
     * @return the resolved entry
     * @throws IllegalStateException if entry is not in PENDING status
     */
    ReconcileQueueEntity resolve(UUID recId, String notes);

    /**
     * Create a new reconciliation queue entry comparing internal vs external quantities.
     *
     * <p>Calculates a confidence score: 1.0 for exact match, decreasing as the
     * difference grows. Formula: 1 - (|internal - external| / max(internal, external)).
     * Clamped to 0 minimum.</p>
     *
     * @param facilityId  the facility identifier
     * @param storeId     the store identifier
     * @param itemCode    the item code
     * @param batch       the batch number
     * @param internalQty the internal stock quantity
     * @param externalQty the external system stock quantity
     * @return the created reconciliation entry
     */
    ReconcileQueueEntity createEntry(UUID facilityId, UUID storeId, String itemCode,
                                      String batch, int internalQty, int externalQty);
}
