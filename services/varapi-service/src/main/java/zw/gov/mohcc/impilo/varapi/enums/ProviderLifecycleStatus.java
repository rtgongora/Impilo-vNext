package zw.gov.mohcc.impilo.varapi.enums;

/**
 * Provider lifecycle status representing the overall regulatory standing.
 *
 * <p>{@link #PRELOADED} and {@link #CLAIMED} formalise the bootstrap-chain states that
 * {@code ProviderBootstrapService} historically wrote as untyped strings — the string
 * values are identical, so no stored data changes.</p>
 */
public enum ProviderLifecycleStatus {
    /** Bulk-preloaded skeleton profile awaiting self-claim (bootstrap chain, L3 W6). */
    PRELOADED,
    /** Preloaded profile claimed by its real person (Health ID bound). */
    CLAIMED,
    DRAFT,
    APPLICATION_IN_PROGRESS,
    UNDER_VERIFICATION,
    PENDING_REVIEW,
    PENDING_COMMITTEE_REVIEW,
    REGISTERED,
    LICENCED_ACTIVE,
    LICENCE_DUE_FOR_RENEWAL,
    RENEWAL_IN_PROGRESS,
    RESTRICTED,
    SUSPENDED,
    LAPSED,
    RESTORATION_IN_PROGRESS,
    RETIRED,
    DECEASED,
    REMOVED
}
