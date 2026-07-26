package zw.gov.mohcc.impilo.reproductive.dating;

import java.time.LocalDate;
import java.util.List;

/**
 * A dating decision: which basis was adopted, what it displaced, and why.
 *
 * <p>The superseded basis is carried deliberately. An estimated delivery date changes what counts as
 * preterm, when a woman is post-term, which antenatal contacts are due and whether a growth
 * measurement is reassuring — so an EDD that moves without anyone deciding it should is one of the
 * more dangerous silent changes in the record. Every revision therefore states both dates, the
 * discrepancy, the tolerance that was applied, and a rationale.
 *
 * @param redatingApplied false records a basis that was considered and REJECTED. "We saw the scan
 *                        and kept the LMP dating" is clinical information, and nothing in this
 *                        estate could express it before.
 */
public record PregnancyDating(
        LocalDate estimatedDeliveryDate,
        LocalDate pregnancyStartDate,
        DatingMethod method,
        DatingConfidence confidence,
        Integer plusOrMinusDays,
        LocalDate datedOn,
        DatingBasis adoptedBasis,
        DatingBasis supersededBasis,
        Integer discrepancyDays,
        Integer toleranceDays,
        boolean redatingApplied,
        List<DatingBasis> consideredBases,
        String rationale,
        String contentVersion) {

    public Integer gestationalAgeDaysOn(LocalDate on) {
        return EddCalculator.gestationalAgeDays(estimatedDeliveryDate, on);
    }

    public GestationalAge gestationalAgeOn(LocalDate on) {
        return EddCalculator.gestationalAge(estimatedDeliveryDate, on);
    }

    /** How much a clinician should trust the date, derived from the method that produced it. */
    public enum DatingConfidence {
        /** Conception known exactly. */
        EXACT,
        /** Early ultrasound: about ±5 days. */
        HIGH,
        /** Early second-trimester ultrasound or a certain LMP: about ±10 days. */
        MODERATE,
        /** Uncertain dates, a late scan, or a clinical estimate. */
        LOW,
        /** Not dated. */
        UNKNOWN;

        public static DatingConfidence forMethod(DatingMethod method) {
            if (method == null) {
                return UNKNOWN;
            }
            return switch (method) {
                case ASSISTED_CONCEPTION -> EXACT;
                case ULTRASOUND_CRL_FIRST_TRIMESTER -> HIGH;
                case ULTRASOUND_EARLY_SECOND_TRIMESTER, LMP_CERTAIN -> MODERATE;
                case ULTRASOUND_LATE_SECOND_OR_THIRD, LMP_UNCERTAIN,
                     SYMPHYSIS_FUNDAL_HEIGHT, CLINICAL_ESTIMATE -> LOW;
                case UNKNOWN -> UNKNOWN;
            };
        }

        /** Nominal uncertainty in days, for display beside a gestational age. */
        public static Integer plusOrMinusDays(DatingMethod method) {
            if (method == null) {
                return null;
            }
            return switch (method) {
                case ASSISTED_CONCEPTION -> 0;
                case ULTRASOUND_CRL_FIRST_TRIMESTER -> 5;
                case ULTRASOUND_EARLY_SECOND_TRIMESTER -> 10;
                case LMP_CERTAIN -> 14;
                case ULTRASOUND_LATE_SECOND_OR_THIRD -> 21;
                case LMP_UNCERTAIN, SYMPHYSIS_FUNDAL_HEIGHT, CLINICAL_ESTIMATE -> 28;
                case UNKNOWN -> null;
            };
        }
    }
}
