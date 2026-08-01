package zw.gov.mohcc.impilo.tshepo.contracts.v1;

/**
 * Canonical trust challenge outcomes. Exact set — do not invent synonyms on the wire.
 */
public enum TrustChallengeDecision {
    ALLOW,
    DENY,
    AUTHENTICATION_REQUIRED,
    STEP_UP_REQUIRED,
    CONTEXT_REQUIRED,
    AUTHORITY_REQUIRED,
    CONSENT_REQUIRED,
    APPROVAL_REQUIRED,
    BREAK_GLASS_AVAILABLE,
    REAUTHENTICATION_REQUIRED,
    RECOVERY_REQUIRED,
    TEMPORARILY_UNAVAILABLE
}
