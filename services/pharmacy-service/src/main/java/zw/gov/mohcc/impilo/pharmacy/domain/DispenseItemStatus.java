package zw.gov.mohcc.impilo.pharmacy.domain;

/**
 * Lifecycle states for an individual dispense item ({@code rx_dispense_items.status}).
 *
 * <p>Tracks the progress of a single drug line-item through the
 * picking and dispensing workflow.</p>
 */
public enum DispenseItemStatus {

    /** Item awaiting picking from stock. */
    PENDING,

    /** Item picked from shelf/bin, awaiting final dispense confirmation. */
    PICKED,

    /** Item dispensed to patient or delegate. Terminal state. */
    DISPENSED,

    /** Item substituted with an alternative drug per substitution rules. */
    SUBSTITUTED,

    /** Item out of stock; awaiting resupply or alternative action. */
    OUT_OF_STOCK,

    /** Item backordered; awaiting resupply from eLMIS. */
    BACKORDERED,

    /** Item returned by patient after dispensing. */
    RETURNED,

    /** Item wasted (e.g. breakage, contamination during handling). */
    WASTED,

    /** Item cancelled (e.g. order reversal or cancellation). Terminal state. */
    CANCELLED
}
