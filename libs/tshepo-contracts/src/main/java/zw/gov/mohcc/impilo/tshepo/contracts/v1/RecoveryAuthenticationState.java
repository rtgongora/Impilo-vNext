package zw.gov.mohcc.impilo.tshepo.contracts.v1;

/**
 * Recovery is a constrained trust state — never silently equivalent to ordinary workforce AAL2.
 */
public enum RecoveryAuthenticationState {
    /** No recovery authentication in this session. */
    NONE,
    /**
     * Authenticated via recovery codes (or equivalent). Sensitive, administrative, regulatory,
     * financial and clinical work remains blocked until a new approved factor is enrolled.
     */
    CONSTRAINED_RECOVERY
}
