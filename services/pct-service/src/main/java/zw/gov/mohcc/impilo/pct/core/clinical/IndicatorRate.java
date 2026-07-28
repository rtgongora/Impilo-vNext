package zw.gov.mohcc.impilo.pct.core.clinical;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A rate that refuses to exist when its denominator is empty.
 *
 * <p>Every §21 indicator that divides goes through here. The rule it enforces is single and absolute:
 * <strong>a rate over a zero denominator is not 0% and not 100% — it is undefined.</strong> A clinic
 * that enrolled nobody has not achieved 0% control, and a clinic with one patient assessed as
 * controlled has not achieved national success. Both readings have been made from indicator screens
 * that printed a number where there was no denominator, and both are worse than a blank.</p>
 *
 * <p>So the numerator and the denominator are always on the response beside the rate, the rate is
 * {@code null} rather than a number when the denominator is zero, and the response carries the reason
 * in words. A consumer that renders {@code rate} without reading {@code denominator} shows an empty
 * cell, which is the honest failure.</p>
 */
public final class IndicatorRate {

    private IndicatorRate() {}

    /**
     * @param basis what the denominator actually is, in a sentence — a rate whose denominator is not
     *              stated cannot be checked by the person reading it
     */
    public static Map<String, Object> of(long numerator, long denominator, String basis) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numerator", numerator);
        m.put("denominator", denominator);
        if (denominator == 0) {
            m.put("rate", null);
            m.put("rate_undefined_reason",
                    "The denominator is zero (" + basis + "), so this rate is undefined. It is not 0% "
                    + "and it is not 100%; nothing about performance can be read from it.");
        } else {
            m.put("rate", BigDecimal.valueOf(numerator)
                    .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                    .doubleValue());
        }
        m.put("denominator_basis", basis);
        return m;
    }
}
