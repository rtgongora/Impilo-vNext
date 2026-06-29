package zw.gov.mohcc.impilo.inventory.domain;

/**
 * Lifecycle status of a stock reservation ({@code inv_stock_reservations.status}).
 */
public enum ReservationStatus {

    /** Holding stock; reduces available quantity. */
    ACTIVE,

    /** Released back to available stock without consumption. */
    RELEASED,

    /** Fulfilled — the reserved stock was issued/consumed via a ledger event. */
    CONSUMED,

    /** Expired before fulfilment; no longer holds stock. */
    EXPIRED
}
