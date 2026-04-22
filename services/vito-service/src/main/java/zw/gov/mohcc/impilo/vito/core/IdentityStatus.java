package zw.gov.mohcc.impilo.vito.core;

/**
 * Lifecycle states for client identity records.
 */
public enum IdentityStatus {
    DRAFT,
    PROVISIONAL,
    REGISTERED,
    PENDING_VERIFICATION,
    PENDING_MATCH_REVIEW,
    VERIFIED,
    ACTIVE,
    FLAGGED_FOR_REVIEW,
    RESTRICTED,
    INACTIVE,
    DECEASED,
    MERGED
}
