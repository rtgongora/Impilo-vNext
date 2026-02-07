package zw.gov.mohcc.impilo.vito.core;

/**
 * WHO SMART Guidelines (L3/L4) identity states.
 *
 * Every client identity transitions through these states:
 *   PROVISIONAL → VERIFIED → ACTIVE → INACTIVE → DECEASED
 *
 * Provisional: identity issued offline or with minimal proofing.
 *              Limited access until verification completes.
 * Verified:    identity proofing complete (biometric, document, or in-person).
 *              Full access to health services.
 * Active:      alias to Verified — full status after proofing.
 * Inactive:    temporarily suspended (e.g., investigation, duplicate).
 * Deceased:    terminal state — triggered by UBOMI death notification.
 * Merged:      record merged into another — soft tombstone.
 */
public enum IdentityStatus {
    /** Offline or minimally proofed — limited access */
    PROVISIONAL,

    /** Identity proofing complete — full access */
    VERIFIED,

    /** Active and in good standing (alias for VERIFIED in some flows) */
    ACTIVE,

    /** Temporarily suspended */
    INACTIVE,

    /** Terminal state — death registered via UBOMI CRVS */
    DECEASED,

    /** Merged into another record (soft tombstone, points to survivor) */
    MERGED
}
