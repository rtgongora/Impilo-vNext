package zw.gov.mohcc.impilo.sharedkernel.events;

/**
 * Controls which event envelope format(s) the outbox publisher emits.
 */
public enum EmitMode {

    /** Emit only legacy (pre-v1.1) event format. */
    LEGACY_ONLY,

    /** Emit only v1.1 envelope format. */
    V1_1_ONLY,

    /** Emit both legacy and v1.1 envelopes (dual-write migration mode). */
    DUAL
}
