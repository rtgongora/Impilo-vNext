package zw.gov.mohcc.impilo.inventory.domain;

/** Outcome of a resolved cold-chain excursion. */
public enum ExcursionResolution {
    /** Stock assessed safe and returned to use. */
    RELEASED,
    /** Stock written off as wastage. */
    WASTAGE
}
