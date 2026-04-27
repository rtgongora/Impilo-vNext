package zw.gov.mohcc.impilo.mvumo.domain;

/**
 * Consent request lifecycle (subset; extend as product matures).
 */
public enum ConsentRequestState {
    DRAFT,
    PENDING_EXPLANATION,
    EXPLANATION_PROVIDED,
    PENDING_IDENTITY_VERIFICATION,
    PENDING_SIGNATURE,
    GRANTED,
    PARTIALLY_GRANTED,
    REFUSED,
    WITHDRAWN,
    EXPIRED,
    SUPERSEDED,
    REVOKED_BY_POLICY,
    EMERGENCY_OVERRIDE,
    INVALIDATED,
    OFFLINE_PENDING_SYNC,
    SYNC_CONFLICT,
    RECONCILED
}
