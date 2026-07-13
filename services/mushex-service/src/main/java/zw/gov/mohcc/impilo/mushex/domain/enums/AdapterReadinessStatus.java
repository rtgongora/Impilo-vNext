package zw.gov.mohcc.impilo.mushex.domain.enums;

/**
 * Conservative readiness state for a payment-rail adapter. Surfaced via
 * {@code GET /mushex/v1/platform/adapter-readiness} and read-only on the
 * canonical finance platform admin page.
 *
 * <p>The state is computed deterministically from <em>configuration</em> and
 * <em>registry presence</em> only. It never reads credentials, never opens a
 * connection to a payment provider, and never causes money movement.
 *
 * <p>Live health probing (e.g. round-trip pings, credential-validity calls)
 * is explicitly out of scope; a future {@code DEGRADED} state is reserved for
 * when real adapters provide a {@code isAvailable()} signal — see §15 of
 * {@code docs/design/g4-rail-selection-policy.md} and Phase 2 of
 * {@code docs/release/mushex-costa-finance-phase-1-release-readiness.md}.
 */
public enum AdapterReadinessStatus {
    /** No Spring bean for this {@link AdapterType} is present in {@code AdapterRegistry}. */
    NOT_REGISTERED,

    /** A bean exists but the operator has explicitly disabled the rail via configuration. */
    DISABLED,

    /**
     * The SANDBOX rail is enabled. It is never live-capable and is therefore
     * the only adapter that can be "ready" without credentials.
     */
    READY_SANDBOX,

    /**
     * A real-money rail is enabled but the operator has not declared that
     * credentials are configured. The rail cannot be used for live routing.
     */
    CREDENTIALS_MISSING,

    /**
     * A real-money rail is enabled and credentials are declared, but the adapter
     * implementation itself reports it is not live-capable (a stub). The attempt-time
     * safety gate will still block this rail, so it is never shown as ready — a
     * READY_LIVE here would tell an operator the rail works when it cannot.
     */
    STUB_NOT_LIVE_CAPABLE,

    /**
     * A real-money rail is enabled, the operator has declared that credentials
     * are configured, AND the adapter implementation reports it is live-capable.
     * Only a rail in this state can actually move money.
     */
    READY_LIVE,

    /**
     * Reserved for a future stage when real adapters report runtime health
     * signals. Not produced by the current readiness service.
     */
    DEGRADED
}
