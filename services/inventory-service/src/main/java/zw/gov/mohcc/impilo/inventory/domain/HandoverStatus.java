package zw.gov.mohcc.impilo.inventory.domain;

/**
 * Lifecycle states of a stock handover between actors ({@code inv_handovers.status}).
 *
 * <p>A handover records the formal transfer of custody over a store's
 * inventory between two staff members (e.g. shift change). Both parties
 * must sign to complete the handover.</p>
 */
public enum HandoverStatus {

    /** Handover record created; awaiting outgoing actor's signature. */
    INITIATED,

    /** Outgoing actor has signed off on the stock state. */
    OUTGOING_SIGNED,

    /** Incoming actor has signed off confirming receipt of stock custody. */
    INCOMING_SIGNED,

    /** Handover fully acknowledged by both parties. */
    COMPLETED,

    /** Handover cancelled before both signatures were obtained. */
    CANCELLED
}
