package zw.gov.mohcc.impilo.vito.core;

/**
 * Reasons for card revocation. Used in the Secure Handover process
 * to determine whether wallet balances and DID should be transferred.
 */
public enum RevocationReason {
    LOST,
    STOLEN,
    DAMAGED,
    EXPIRED,
    REPLACED
}
