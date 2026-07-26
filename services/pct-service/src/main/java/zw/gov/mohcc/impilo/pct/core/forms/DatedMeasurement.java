package zw.gov.mohcc.impilo.pct.core.forms;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A clinical measurement together with when it was true.
 *
 * <p>Adult decision support turns on numbers that decay. A weight from two years ago is not a
 * dosing weight; an eGFR from before an acute illness is not the kidney function you are dosing
 * against today. Carrying the bare number would make a stale value indistinguishable from a current
 * one — and unlike a missing value, a stale one looks entirely usable, so nothing prompts anyone to
 * go and measure again.</p>
 *
 * <p><strong>This type does not decide what counts as stale.</strong> How old is too old is a
 * clinical judgement that varies by drug and by context — days for a creatinine in acute kidney
 * injury, months for a maintenance weight — so it belongs in governed rule content, not in a data
 * carrier. The measurement reports its age; the rule decides what to do about it.</p>
 *
 * @param value      the measured value; null means never measured
 * @param unit       the unit the value is in; carried so a rule can refuse a unit it does not expect
 * @param measuredOn when it was measured; null with a non-null value means the date was not
 *                   recorded, which is itself a reason to treat the value with suspicion
 * @param source     how it was arrived at — for eGFR the equation (CKD-EPI, MDRD), for a weight
 *                   whether it was measured or stated
 */
public record DatedMeasurement(Double value, String unit, LocalDate measuredOn, String source) {

    private static final DatedMeasurement UNKNOWN = new DatedMeasurement(null, null, null, null);

    /** Never measured. Distinct from measured-and-normal, and never to be read as normal. */
    public static DatedMeasurement unknown() {
        return UNKNOWN;
    }

    public static DatedMeasurement of(double value, String unit, LocalDate measuredOn, String source) {
        return new DatedMeasurement(value, unit, measuredOn, source);
    }

    /** True only when there is an actual measurement. A null value is not a normal one. */
    public boolean known() {
        return value != null;
    }

    /**
     * Completed days since the measurement, or null when that cannot be established — either
     * because nothing was measured or because nobody recorded when.
     *
     * <p>A known value with an unknown date returns null rather than zero. Zero would claim it was
     * taken today, which is the most reassuring answer available and the one least supported by the
     * record.</p>
     */
    public Long ageInDays(LocalDate today) {
        if (value == null || measuredOn == null || today == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(measuredOn, today);
    }

    /**
     * Whether this measurement is older than the given number of days.
     *
     * <p>Returns null — not false — when the age cannot be established, so a caller cannot treat
     * "we don't know how old this is" as "it is fresh". Three-valued on purpose; the two-valued
     * version of this question is how a stale value gets used.</p>
     */
    public Boolean olderThan(int days, LocalDate today) {
        Long age = ageInDays(today);
        return age == null ? null : age > days;
    }
}
