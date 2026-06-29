package zw.gov.mohcc.impilo.inventory.domain;

/** How a household stock item came to be held. */
public enum HomeStockSource {
    /** Dispensed to the client by a facility/pharmacy. */
    DISPENSED,
    /** Recorded by a caregiver on the client's behalf. */
    CAREGIVER,
    /** Self-recorded by the client. */
    SELF
}
