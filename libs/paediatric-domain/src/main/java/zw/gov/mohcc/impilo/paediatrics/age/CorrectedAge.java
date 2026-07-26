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
 * <p>Correction applies only when gestational age at birth is known and below term
 * ({@value #TERM_GESTATION_WEEKS} weeks), and is withdrawn once corrected age passes
 * {@value #CORRECTION_CEASES_MONTHS} months — the convention used by WHO/national
 * newborn follow-up guidance. Outside those bounds the chronological age is returned
 * unchanged, so a caller can always use the corrected value safely.</p>
 */
public final class CorrectedAge {

    public static final int TERM_GESTATION_WEEKS = 37;
    public static final int CORRECTION_CEASES_MONTHS = 24;

    private static final int DAYS_PER_WEEK = 7;
    private static final int APPROX_DAYS_PER_MONTH = 30;

    private CorrectedAge() {
    }

    /** True when the recorded gestational age indicates preterm birth. */
    public static boolean isPreterm(Integer gestationalAgeWeeks) {
        return gestationalAgeWeeks != null
                && gestationalAgeWeeks > 0
                && gestationalAgeWeeks < TERM_GESTATION_WEEKS;
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
        int correctionDays = (TERM_GESTATION_WEEKS - gestationalAgeWeeks) * DAYS_PER_WEEK;
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
