package zw.gov.mohcc.impilo.costa.domain.enums;

/** Lifecycle of an emergency-override deferred charge (Journey Law 1). */
public enum ReconciliationState {
    /** Awaiting post-care reconciliation (identity link + settlement routing). */
    PENDING,
    /** Routed to settle/claim/waiver and closed. */
    RECONCILED,
    /** Reconciliation no longer required (e.g. duplicate, written off via waiver elsewhere). */
    CANCELLED
}
