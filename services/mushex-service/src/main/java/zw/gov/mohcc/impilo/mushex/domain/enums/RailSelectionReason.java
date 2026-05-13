package zw.gov.mohcc.impilo.mushex.domain.enums;

/**
 * Machine-readable reason explaining why a particular payment rail was selected for a
 * {@code PaymentIntent}.
 *
 * <p>Recorded under {@code payment_intents.metadata.rail_selection.reason} and on the
 * {@code INTENT_CREATED} outbox event, per the G-4 design document
 * ({@code docs/design/g4-rail-selection-policy.md}).
 *
 * <p>Note: enum values that begin with {@code REJECTED_} are not persisted on an intent;
 * they are surfaced via {@code IllegalArgumentException} with the corresponding stable
 * error code (see {@code docs/design/g4-rail-selection-policy.md} §10).
 */
public enum RailSelectionReason {
    /** Caller supplied a {@code preferredRailAdapter} that maps to a registered adapter. */
    EXPLICIT_PREFERRED,

    /** Caller did not supply a preference; the configured {@code defaultRail} was used. */
    DEFAULT_NO_PREFERENCE,

    /**
     * Caller requested direct-gateway mode and deployment policy permits it; the first
     * registered non-SANDBOX rail was used.
     */
    DIRECT_GATEWAY_REQUESTED,

    /**
     * Caller's preferred rail is not registered in this environment and fallback was
     * permitted; SANDBOX was used.
     */
    PREFERRED_UNAVAILABLE_FALLBACK,

    /**
     * The {@code mushex.rail-selection.enabled} safety switch is off, or the request
     * carried {@code impilo_simulation=true} metadata, forcing SANDBOX regardless of
     * preference.
     */
    SAFETY_SWITCH_FORCED_SANDBOX,

    /** Rejection: caller's {@code preferredRailAdapter} value is not a valid {@link AdapterType}. */
    REJECTED_UNKNOWN_RAIL,

    /**
     * Rejection: caller's preferred rail is a known {@link AdapterType} but no adapter
     * is registered for it, and {@code allowFallback=false}.
     */
    REJECTED_UNAVAILABLE_NO_FALLBACK
}
