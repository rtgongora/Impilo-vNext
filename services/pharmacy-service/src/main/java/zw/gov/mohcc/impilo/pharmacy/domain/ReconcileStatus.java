package zw.gov.mohcc.impilo.pharmacy.domain;

/**
 * States for eLMIS reconciliation queue entries ({@code rx_reconcile_queue.status}).
 *
 * <p>Tracks the progress of stock discrepancy resolution between
 * the pharmacy service's internal records and the external eLMIS.</p>
 */
public enum ReconcileStatus {

    /** Discrepancy detected; awaiting review. */
    PENDING,

    /** Internal and external records matched (auto or manual). */
    MATCHED,

    /** Discrepancy resolved with an adjustment or explanation. Terminal state. */
    RESOLVED,

    /** Discrepancy rejected as invalid or duplicate. Terminal state. */
    REJECTED
}
