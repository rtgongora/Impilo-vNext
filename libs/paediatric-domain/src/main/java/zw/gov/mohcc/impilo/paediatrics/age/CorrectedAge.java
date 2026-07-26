package zw.gov.mohcc.impilo.paediatrics.age;

import java.time.LocalDate;

/**
 * Corrected (post-menstrual) age for children born preterm.
 *
 * <p>A baby born at 32 weeks is two months younger, developmentally and nutritionally,
 * than a term baby of the same chronological age. Growth z-scores and developmental
 * expectations are read against corrected age until the correction stops being clinically
 * meaningful.</p>
 *
 * <p><strong>Two questions, two constants.</strong> <em>Whether</em> to correct is decided at
 * {@value #PRETERM_THRESHOLD_WEEKS} weeks — birth before that is preterm. <em>How far</em> to
 * correct is measured back to {@value #TERM_CORRECTION_TARGET_WEEKS} weeks, full term. These
 * were a single constant until they were separated here, which made the correction target the
 * threshold: a baby born at 30 weeks was corrected by 7 weeks where the convention says 10, so
 * every preterm child's growth was read about three weeks "old" — which is to say slightly
 * worse than it actually was. The two numbers answer different questions and must not be
 * collapsed back into one.</p>
 *
 * <p>Correction applies only when gestational age at birth is known and preterm, and is
 * withdrawn once corrected age passes {@value #CORRECTION_CEASES_MONTHS} months — the
 * convention used by WHO/national newborn follow-up guidance. Outside those bounds the
 * chronological age is returned unchanged, so a caller can always use the corrected value
 * safely.</p>
 *
 * <p>Two step changes are inherent to the convention rather than defects in it, and are left
 * visible rather than smoothed away. At the threshold, a baby born at 36 weeks is corrected by
 * four weeks and one born at 37 weeks not at all. At the far end, correction ceases outright
 * rather than tapering. Both are how the guidance is written; a stored score is interpreted
 * against the engine version that produced it, not against a curve fitted across them.</p>
 */
public final class CorrectedAge {

    /**
     * Birth before this gestational age is preterm. This decides <em>whether</em> a correction
     * applies — it is not the target the correction is measured to.
     */
    public static final int PRETERM_THRESHOLD_WEEKS = 37;

    /**
     * Full term: the gestational age that corrected age is measured back to. A preterm baby's
     * corrected age is their chronological age less the weeks they were born short of this.
     */
    public static final int TERM_CORRECTION_TARGET_WEEKS = 40;

    public static final int CORRECTION_CEASES_MONTHS = 24;

    private static final int DAYS_PER_WEEK = 7;
    private static final int APPROX_DAYS_PER_MONTH = 30;

    private CorrectedAge() {
    }

    /** True when the recorded gestational age indicates preterm birth. */
    public static boolean isPreterm(Integer gestationalAgeWeeks) {
        return gestationalAgeWeeks != null
                && gestationalAgeWeeks > 0
                && gestationalAgeWeeks < PRETERM_THRESHOLD_WEEKS;
    }

    /**
     * Corrected age in days, or the chronological age when no correction applies.
     * Returns null only when chronological age itself is unknown.
     */
    public static Integer correctedAgeDays(Integer chronologicalAgeDays, Integer gestationalAgeWeeks) {
        if (chronologicalAgeDays == null) {
            return null;
        }
        if (!isPreterm(gestationalAgeWeeks)) {
            return chronologicalAgeDays;
        }
        int weeksShortOfTerm = TERM_CORRECTION_TARGET_WEEKS - gestationalAgeWeeks;
        int correctionDays = weeksShortOfTerm * DAYS_PER_WEEK;
        int corrected = chronologicalAgeDays - correctionDays;
        if (corrected < 0) {
            corrected = 0;
        }
        if (corrected > CORRECTION_CEASES_MONTHS * APPROX_DAYS_PER_MONTH) {
            return chronologicalAgeDays;
        }
        return corrected;
    }

    public static Integer correctedAgeDays(LocalDate dateOfBirth, LocalDate reference, Integer gestationalAgeWeeks) {
        return correctedAgeDays(AgeCalculator.ageDays(dateOfBirth, reference), gestationalAgeWeeks);
    }

    /** True when the returned corrected age actually differs from the chronological age. */
    public static boolean correctionApplied(Integer chronologicalAgeDays, Integer gestationalAgeWeeks) {
        Integer corrected = correctedAgeDays(chronologicalAgeDays, gestationalAgeWeeks);
        return corrected != null && chronologicalAgeDays != null && !corrected.equals(chronologicalAgeDays);
    }
}
