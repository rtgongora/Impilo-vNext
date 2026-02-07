package zw.gov.mohcc.impilo.pharmacy.domain;

/**
 * Lifecycle states for a dispense order ({@code rx_dispense_orders.status}).
 *
 * <p>Transition path (happy flow):
 * PENDING -> ACCEPTED -> PICKING -> READY -> DISPENSED.
 * PARTIALLY_DISPENSED can lead to DISPENSED or CANCELLED.
 * REJECTED is used for backorders (stock unavailability).
 * CANCELLED is a terminal state reachable from most non-terminal states.</p>
 */
public enum DispenseStatus {

    /** Order received from OROS, awaiting pharmacist acceptance. */
    PENDING,

    /** Pharmacist has accepted the order and it is ready for picking. */
    ACCEPTED,

    /** Items are being picked from stock (FEFO). */
    PICKING,

    /** All items picked, ready for final dispense confirmation or handoff. */
    READY,

    /** All items dispensed successfully. Terminal state. */
    DISPENSED,

    /** Some items dispensed; others still pending or backordered. */
    PARTIALLY_DISPENSED,

    /** Order cancelled before completion. Terminal state. */
    CANCELLED,

    /** Order rejected (e.g. backorder due to stock unavailability). Terminal state. */
    REJECTED
}
