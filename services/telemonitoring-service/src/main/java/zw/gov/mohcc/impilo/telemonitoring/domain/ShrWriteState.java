package zw.gov.mohcc.impilo.telemonitoring.domain;

/**
 * Single-designated-writer ledger state for a reading's monitoring-band Observation
 * (OF-B25, §14.5 step 14 — closes the R72 three-writer sprawl).
 *
 * <p>Honesty law: {@code WRITTEN} (and the {@code shr_written_at}/{@code shr_ref}
 * columns) are reached ONLY on gateway outcome SUCCESS. Every failure is counted and
 * coded; retries are bounded — after the cap the row parks in {@code EXHAUSTED},
 * visible and recoverable, never silently absorbed (§14.5 invariant).</p>
 */
public enum ShrWriteState {
    /** Quarantined reading, or its plan is not ACTIVE — the writer must not project it. */
    NOT_ELIGIBLE,
    /** Awaiting its first write attempt. */
    PENDING,
    /** At least one attempt failed with a coded outcome; the bounded sweep will retry. */
    RETRYING,
    /** Gateway confirmed SUCCESS — the Observation is in the record. */
    WRITTEN,
    /** Retry budget spent without SUCCESS — parked for operator attention. */
    EXHAUSTED
}
