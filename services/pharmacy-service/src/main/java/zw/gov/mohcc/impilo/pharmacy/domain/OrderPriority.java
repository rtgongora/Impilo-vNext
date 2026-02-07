package zw.gov.mohcc.impilo.pharmacy.domain;

/**
 * Priority levels for dispense orders ({@code rx_dispense_orders.priority}).
 *
 * <p>Determines the urgency of the dispensing workflow, affecting
 * queue positioning and SLA targets.</p>
 */
public enum OrderPriority {

    /** Immediate -- life-threatening situation requiring instant dispensing. */
    STAT,

    /** Urgent -- must be dispensed within a short timeframe. */
    URGENT,

    /** Routine -- standard dispensing timeline. */
    ROUTINE
}
