package zw.gov.mohcc.impilo.inventory.domain;

/**
 * States for reconciliation queue entries ({@code inv_reconcile_queue.status}).
 *
 * <p>Reconciliation compares internal inventory on-hand quantities with
 * data reported by an external system (e.g. eLMIS). Discrepancies are
 * queued for operator review.</p>
 */
public enum ReconcileStatus {

    /** Entry awaiting operator review. */
    PENDING,

    /** Internal and external quantities reconciled automatically (within tolerance). */
    MATCHED,

    /** Operator has reviewed and manually resolved the discrepancy. */
    RESOLVED,

    /** Operator has rejected the external record as invalid. */
    REJECTED
}
