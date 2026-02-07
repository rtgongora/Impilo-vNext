package zw.gov.mohcc.impilo.tshepo.offline.exception;

import java.util.UUID;

/**
 * Thrown when a requested reconciliation batch is not found.
 */
public class ReconciliationBatchNotFoundException extends OfflineServiceException {

    public ReconciliationBatchNotFoundException(UUID batchId) {
        super("BATCH_NOT_FOUND", "Reconciliation batch not found: " + batchId);
    }
}
