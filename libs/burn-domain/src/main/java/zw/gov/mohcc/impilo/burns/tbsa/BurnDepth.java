package zw.gov.mohcc.impilo.burns.tbsa;

/**
 * Depth of a burn in one body region.
 *
 * <p><b>The safety-critical distinction is {@link #SUPERFICIAL}.</b> A superficial (epidermal,
 * historically "first degree") burn — sunburn being the familiar example — is erythema without
 * blistering, and it is <b>excluded from the %TBSA used for fluid resuscitation</b>. Including it
 * inflates the burn size and therefore the calculated fluid volume, and over-resuscitation causes
 * its own harm: compartment syndrome, pulmonary oedema, raised intra-abdominal pressure.
 *
 * <p>The exclusion is expressed here as a property of the depth rather than as a rule a caller has
 * to remember, because a caller who forgets it produces a plausible number that is wrong in the
 * dangerous direction.
 */
public enum BurnDepth {

    /** Erythema only, no blistering. Counted in the burn map, excluded from resuscitation %TBSA. */
    SUPERFICIAL(false),

    /** Blistered, moist, blanching, painful. Counts toward %TBSA. */
    SUPERFICIAL_PARTIAL(true),

    /** Blistered, drier, sluggish or absent blanching, reduced sensation. Counts toward %TBSA. */
    DEEP_PARTIAL(true),

    /** Leathery, non-blanching, insensate. Counts toward %TBSA. */
    FULL_THICKNESS(true),

    /** Involving fascia, muscle or bone. Counts toward %TBSA. */
    DEEP_FULL_THICKNESS(true);

    private final boolean countsTowardResuscitation;

    BurnDepth(boolean countsTowardResuscitation) {
        this.countsTowardResuscitation = countsTowardResuscitation;
    }

    /**
     * Whether a burn of this depth contributes to the %TBSA that drives fluid resuscitation.
     * False only for {@link #SUPERFICIAL}.
     */
    public boolean countsTowardResuscitation() {
        return countsTowardResuscitation;
    }
}
