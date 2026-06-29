package zw.gov.mohcc.impilo.inventory.domain;

/**
 * Lifecycle status of a recall ({@code inv_recall_events.status}).
 */
public enum RecallStatus {

    /** Recall is active; affected batches are blocked from use. */
    OPEN,

    /** Recall response complete and closed out. */
    CLOSED
}
