package zw.gov.mohcc.impilo.inventory.domain;

/**
 * Classification of stock ledger events ({@code inv_ledger_events.event_type}).
 *
 * <p>Each ledger event records a signed quantity delta (positive for inflows,
 * negative for outflows) against a specific item/batch/bin combination. The
 * event type indicates the business reason for the stock movement and drives
 * the on-hand projection recalculation.</p>
 */
public enum LedgerEventType {

    /** Stock increased due to receipt from a supplier or eLMIS delivery. */
    RECEIPT,

    /** Stock decreased due to issue to a ward, pharmacy, or other consumer. */
    ISSUE,

    /** Stock decreased due to transfer to another store or facility. */
    TRANSFER_OUT,

    /** Stock increased due to transfer received from another store or facility. */
    TRANSFER_IN,

    /** Stock adjusted (up or down) via a manual correction or administrative action. */
    ADJUSTMENT,

    /** Stock decreased due to expiry, breakage, contamination, or spoilage. */
    WASTAGE,

    /** Stock increased due to return from a ward, pharmacy, or patient. */
    RETURN,

    /** Snapshot quantity recorded during a physical stock count session. */
    COUNT,

    /** Variance adjustment applied after a stock count is approved. */
    COUNT_ADJUST
}
