package zw.gov.mohcc.impilo.reproductive.history;

/** How a pregnancy ended. */
public enum PregnancyOutcome {

    LIVE_BIRTH,
    STILLBIRTH,
    MISCARRIAGE,
    ECTOPIC,
    MOLAR,
    TERMINATION,
    /** Still in progress. Counts toward gravidity, never toward parity. */
    ONGOING,
    /** Recorded, but the outcome was never captured. Counts toward gravidity and nothing else. */
    UNKNOWN;

    /**
     * Whether this outcome counts as a birth (parity) rather than a loss (abortus).
     *
     * <p>The line between a late miscarriage and a stillbirth is a gestational threshold set by
     * national policy, not by this enum — Zimbabwe's civil registration and WHO's international
     * comparison threshold do not have to agree, and the code must not assume either. So an outcome
     * recorded as STILLBIRTH is a birth on its own say-so, and a MISCARRIAGE is judged against the
     * policy's threshold when the gestation is known.
     *
     * <p>An unknown gestation on a miscarriage resolves to a loss. That is the conservative answer
     * for parity — it is the one that does not inflate a birth count — and it is reported as
     * uncountable for the term/preterm split rather than silently assumed.
     */
    public boolean reachedViability(Integer gestationalAgeDaysAtEnd, LossThresholdPolicy policy) {
        return switch (this) {
            case LIVE_BIRTH, STILLBIRTH -> true;
            case MISCARRIAGE -> gestationalAgeDaysAtEnd != null
                    && policy != null
                    && gestationalAgeDaysAtEnd >= policy.stillbirthThresholdDays();
            case ECTOPIC, MOLAR, TERMINATION, ONGOING, UNKNOWN -> false;
        };
    }

    /** True when this outcome ends the pregnancy. */
    public boolean ended() {
        return this != ONGOING;
    }
}
