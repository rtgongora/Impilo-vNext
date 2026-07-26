package zw.gov.mohcc.impilo.reproductive.dating;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Estimated delivery date and gestational age.
 *
 * <p>The arithmetic is WHO's, taken from the SMART ANC scheduling library {@code ANCS01.cql}:
 * {@code EDD = LMP + 280 days}, and gestational age is whole days since the last menstrual period.
 * It is transcribed here rather than paraphrased so that the one number every maternal rule depends
 * on has a single, citable definition.
 *
 * <p><b>Every method returns null rather than a default when it cannot answer.</b> A pregnancy whose
 * dating is unknown must never quietly become term: the three most consequential errors in this
 * domain are treating an undated pregnancy as term, treating an expired implant as protecting, and
 * recording a grand multipara as nullipara, and all three are defaults presented as measurements.
 * At the point of use a defaulted value is indistinguishable from a real one.
 */
public final class EddCalculator {

    /** Naegele's rule: 280 days from the last menstrual period. WHO ANCS01. */
    public static final int NAEGELE_LMP_TO_EDD_DAYS = 280;

    /** 266 days from conception — 280 minus the two weeks before ovulation that LMP dating counts. */
    public static final int CONCEPTION_TO_EDD_DAYS = 266;

    public static final String CONTENT_VERSION = "impilo-obstetric-dating-1.0.0";
    public static final String CONTENT_SOURCE =
            "WHO SMART ANC scheduling logic (ANCS01): EDD = LMP + 280 days; "
            + "gestational age = days since LMP";

    private EddCalculator() {
    }

    /** EDD from a last menstrual period. Null when the date is unknown. */
    public static LocalDate fromLastMenstrualPeriod(LocalDate lmp) {
        return lmp == null ? null : lmp.plusDays(NAEGELE_LMP_TO_EDD_DAYS);
    }

    /**
     * EDD from a gestational age measured on a known date — an ultrasound report, or a clinical
     * estimate. Works backwards to the notional LMP and forwards again.
     */
    public static LocalDate fromMeasuredGestationalAge(LocalDate measuredOn, Integer gestationalAgeDays) {
        if (measuredOn == null || gestationalAgeDays == null || gestationalAgeDays < 0) {
            return null;
        }
        return measuredOn.plusDays((long) NAEGELE_LMP_TO_EDD_DAYS - gestationalAgeDays);
    }

    /**
     * EDD from assisted conception.
     *
     * <p>The embryo's age at transfer matters and is a common source of a two-day error: a day-5
     * blastocyst transferred today is already five days past conception, so the EDD is
     * {@code transfer + 266 - 5}. Passing null for the embryo age treats the transfer date as the
     * conception date, which is right for insemination and wrong for a blastocyst.
     */
    public static LocalDate fromAssistedConception(LocalDate transferDate, Integer embryoAgeDaysAtTransfer) {
        if (transferDate == null) {
            return null;
        }
        int embryoAge = embryoAgeDaysAtTransfer == null ? 0 : embryoAgeDaysAtTransfer;
        if (embryoAge < 0) {
            return null;
        }
        return transferDate.plusDays((long) CONCEPTION_TO_EDD_DAYS - embryoAge);
    }

    /**
     * The notional start of the pregnancy: the LMP the EDD implies, whether or not one was recorded.
     *
     * <p>Stored on the episode as the single anchor every downstream calculation uses, so that a
     * gestational age computed by a schedule engine and one shown on a chart cannot drift apart.
     */
    public static LocalDate pregnancyStartDate(LocalDate estimatedDeliveryDate) {
        return estimatedDeliveryDate == null
                ? null
                : estimatedDeliveryDate.minusDays(NAEGELE_LMP_TO_EDD_DAYS);
    }

    /** Gestational age in completed days on a given date. Null when the EDD is unknown. */
    public static Integer gestationalAgeDays(LocalDate estimatedDeliveryDate, LocalDate on) {
        LocalDate start = pregnancyStartDate(estimatedDeliveryDate);
        if (start == null || on == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(start, on);
        // Before the notional start the number is meaningless. Returning it would render as a
        // negative gestation on a chart; returning zero would assert a pregnancy that has not begun.
        return days < 0 ? null : (int) days;
    }

    public static GestationalAge gestationalAge(LocalDate estimatedDeliveryDate, LocalDate on) {
        return GestationalAge.ofDays(gestationalAgeDays(estimatedDeliveryDate, on));
    }

    /**
     * Days past the estimated delivery date; negative before it.
     *
     * <p>Unlike {@link #gestationalAgeDays} this may legitimately be negative — "eight days before
     * her date" is a normal thing to say — so the sign is information rather than an error.
     */
    public static Integer daysFromEdd(LocalDate estimatedDeliveryDate, LocalDate on) {
        if (estimatedDeliveryDate == null || on == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(estimatedDeliveryDate, on);
    }

    /** Difference between two estimated delivery dates, in days; null when either is unknown. */
    public static Integer discrepancyDays(LocalDate a, LocalDate b) {
        if (a == null || b == null) {
            return null;
        }
        return (int) Math.abs(ChronoUnit.DAYS.between(a, b));
    }
}
