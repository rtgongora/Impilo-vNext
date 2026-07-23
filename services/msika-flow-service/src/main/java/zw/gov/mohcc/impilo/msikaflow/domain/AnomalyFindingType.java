package zw.gov.mohcc.impilo.msikaflow.domain;

/**
 * OF-B29 (Wave OF-D) — coded anomaly/fairness finding types (Vol II
 * §13.3/§13.7/§21). Every type is deterministic (rule + threshold), never a
 * statistical model — the honest foundation the future model-based stream
 * builds on.
 */
public enum AnomalyFindingType {

    /**
     * §13.3.1 marketplace-side view — a COMMITTED medication selection carrying
     * a prescription-token claim with no observable dispense progress past the
     * claim TTL (pharmacy sweeps the dispense-episode side; this is the
     * complement over marketplace truth).
     */
    CLAIM_WITHOUT_EPISODE,

    /**
     * §13.3.1 complement — a COMMITTED medication selection with NO prescription
     * claim recorded: commitment step 6 claims for medication profiles, so a
     * claimless medication commitment means fulfilment is proceeding outside
     * the token discipline (T10/T11 surface).
     */
    COMMITMENT_WITHOUT_CLAIM,

    /** Selection COMMITTED but no dispatch/dispense/delivery progress past the stall window. */
    STALLED_COMMITMENT,

    /**
     * Same vendor wins repeatedly for the same requester inside a short window
     * (T15/T19 steering surface). The requester enters the pattern key only as
     * an opaque hash — never as an identifier in the finding or event.
     */
    RAPID_REPEAT_WIN,

    /**
     * mf_reservations projection row still PENDING/CONFIRMED past its TTL +
     * grace — either the DURA hold leaked or the release/consume event was
     * missed; both need ops eyes (DURA remains the reservation truth).
     */
    RESERVATION_LEAK,

    /** §21 — one vendor's share of commitments in the window exceeds the concentration threshold. */
    CONCENTRATION_WATCH,

    /**
     * §21 ranking-transparency audit — recomputed ranked-because labels for a
     * committed selection's offer no longer match the captured snapshot: the
     * ranking basis shown to the patient diverges from the persisted record.
     */
    RANKING_DRIFT,

    /**
     * §11.1 makes the ranked-position snapshot mandatory for the fairness
     * audit — a committed selection with no captured snapshot is itself a
     * transparency violation, flagged rather than silently skipped.
     */
    RANKING_SNAPSHOT_MISSING,

    /** §21 no-offer equity — a coarse zone's FAILED_NO_OFFERS rate exceeds the equity threshold. */
    NO_OFFER_EQUITY
}
