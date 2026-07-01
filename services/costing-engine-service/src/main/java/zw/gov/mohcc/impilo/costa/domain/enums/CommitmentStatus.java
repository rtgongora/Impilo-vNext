package zw.gov.mohcc.impilo.costa.domain.enums;

/** Lifecycle of a budget commitment. Invariant: liquidated <= obligated <= committed. */
public enum CommitmentStatus {
    PROPOSED,
    COMMITTED,
    PARTIALLY_LIQUIDATED,
    LIQUIDATED,
    CANCELLED,
    EXPIRED
}
