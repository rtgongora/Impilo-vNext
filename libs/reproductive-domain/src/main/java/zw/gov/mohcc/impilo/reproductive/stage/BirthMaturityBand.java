package zw.gov.mohcc.impilo.reproductive.stage;

/**
 * Maturity at birth, in completed days of gestation (WHO / ACOG).
 *
 * <p>Shared with the clinical rules engine: nearly every neonatal rule is banded on this, and two
 * implementations of "what counts as preterm" would eventually disagree about the same baby.
 *
 * <p>The boundaries are the ones in general obstetric use. "Term" is deliberately not a single band
 * — early term (37+0 to 38+6) carries measurably worse neonatal outcomes than full term, which is
 * the entire reason elective delivery before 39 weeks is discouraged, and a model that collapses
 * them cannot express that.
 */
public enum BirthMaturityBand {

    /** Below 28+0. */
    EXTREMELY_PRETERM(0, 195),
    /** 28+0 to 31+6. */
    VERY_PRETERM(196, 223),
    /** 32+0 to 36+6. */
    MODERATE_TO_LATE_PRETERM(224, 258),
    /** 37+0 to 38+6. */
    EARLY_TERM(259, 272),
    /** 39+0 to 40+6. */
    FULL_TERM(273, 286),
    /** 41+0 to 41+6. */
    LATE_TERM(287, 293),
    /** 42+0 and beyond. */
    POST_TERM(294, Integer.MAX_VALUE);

    private final int minDaysInclusive;
    private final int maxDaysInclusive;

    BirthMaturityBand(int minDaysInclusive, int maxDaysInclusive) {
        this.minDaysInclusive = minDaysInclusive;
        this.maxDaysInclusive = maxDaysInclusive;
    }

    public int minDaysInclusive() {
        return minDaysInclusive;
    }

    public int maxDaysInclusive() {
        return maxDaysInclusive;
    }

    public boolean covers(int gestationalAgeDays) {
        return gestationalAgeDays >= minDaysInclusive && gestationalAgeDays <= maxDaysInclusive;
    }

    public boolean preterm() {
        return this == EXTREMELY_PRETERM || this == VERY_PRETERM || this == MODERATE_TO_LATE_PRETERM;
    }

    public boolean term() {
        return this == EARLY_TERM || this == FULL_TERM || this == LATE_TERM;
    }

    /** Null when the gestation is unknown or negative — never a default of term. */
    public static BirthMaturityBand ofGestationalAgeDays(Integer gestationalAgeDays) {
        if (gestationalAgeDays == null || gestationalAgeDays < 0) {
            return null;
        }
        for (BirthMaturityBand band : values()) {
            if (band.covers(gestationalAgeDays)) {
                return band;
            }
        }
        return null;
    }

    /**
     * Whether a birth at this gestation is preterm. Null — not false — when the gestation is
     * unknown, because "we do not know how mature this baby is" and "this baby is term" lead to
     * completely different neonatal care.
     */
    public static Boolean preterm(Integer gestationalAgeDays) {
        BirthMaturityBand band = ofGestationalAgeDays(gestationalAgeDays);
        return band == null ? null : band.preterm();
    }
}
