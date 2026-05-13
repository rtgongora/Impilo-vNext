package zw.gov.mohcc.impilo.mushex.domain.enums;

/**
 * Reason an attempt-time rail enforcement decision picked a particular rail.
 * Recorded on {@code PaymentAttemptEntity.enforcement_metadata.reason} and on the
 * {@code ATTEMPT_INITIATED} outbox event payload's {@code enforcementReason} key.
 *
 * <p>See {@code docs/design/phase-3-attempt-time-rail-enforcement-implementation.md}.
 */
public enum RailEnforcementReason {
    /**
     * The intent carried {@code metadata.rail_selection.effective_rail} and the named
     * adapter is registered. This is the common path post-G-4.
     */
    EXPLICIT_METADATA,

    /**
     * The intent has no rail-selection metadata (created before Stage 3.5) — the
     * deployment's configured {@code mushex.rail-selection.default-rail} (SANDBOX by
     * default) is used. The attempt's {@code legacy_intent} flag is set.
     */
    LEGACY_FALLBACK,

    /**
     * The intent metadata carries {@code impilo_simulation=true} — SANDBOX is forced
     * regardless of the persisted effective rail. Mirrors the safety invariant in
     * {@code PaymentIntentService.runRailSelection(...)}.
     */
    SIMULATION_LOCK
}
