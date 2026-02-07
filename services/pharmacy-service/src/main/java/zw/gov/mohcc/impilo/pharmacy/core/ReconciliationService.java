package zw.gov.mohcc.impilo.pharmacy.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.ReconcileQueueEntity;

import java.util.UUID;

/**
 * Service managing stock reconciliation between internal records
 * and external systems (e.g. eLMIS).
 *
 * <p>Provides paginated views of pending reconciliation entries
 * and resolution workflows for operators.</p>
 */
public interface ReconciliationService {

    /**
     * Get pending reconciliation entries for the current tenant.
     */
    Page<ReconcileQueueEntity> getPendingReconciliations(Pageable pageable);

    /**
     * Resolve a reconciliation entry with operational notes.
     */
    ReconcileQueueEntity resolveReconciliation(UUID recId, String notes);
}
