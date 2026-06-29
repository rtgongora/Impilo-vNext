package zw.gov.mohcc.impilo.inventory.domain;

/** Lifecycle status of a client refill request. */
public enum RefillStatus {
    REQUESTED,
    APPROVED,
    FULFILLED,
    REJECTED,
    CANCELLED
}
