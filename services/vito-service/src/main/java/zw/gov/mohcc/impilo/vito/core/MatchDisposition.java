package zw.gov.mohcc.impilo.vito.core;

/**
 * Match resolution dispositions for the probabilistic matching engine.
 * Follows WHO-recommended workflow for identity resolution.
 */
public enum MatchDisposition {
    /** Awaiting review */
    PENDING,

    /** Score above auto-link threshold — linked automatically */
    AUTO_LINKED,

    /** Manually linked by operator after review */
    MANUAL_LINKED,

    /** Rejected — not a match */
    REJECTED,

    /** Deferred for later review */
    DEFERRED
}
