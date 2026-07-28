package zw.gov.mohcc.impilo.clinical.maternal;

import java.util.List;

/**
 * Computes a programme indicator (ANC and PNC coverage, and the like) from evaluated cases, honestly.
 *
 * <p>Pure. It is handed each case already classified against the indicator's numerator and
 * denominator and returns the count; it does no querying and holds no data, so a reported figure
 * reproduces exactly from the case list behind it.
 *
 * <p><b>The safety property is what happens to the cases you could not assess.</b> An indicator is a
 * fraction, and the easy way to make coverage look good is to drop the cases whose numerator you
 * could not determine — the woman whose first-contact status is unknown quietly leaves the
 * denominator, and 60% real coverage reports as 95%. This engine refuses that: an
 * {@link CaseClassification#INDETERMINATE} case stays IN the denominator and counts as NOT in the
 * numerator, and the indeterminate count travels with the result so a rate can never be read without
 * its data quality. A surveillance denominator never silently loses a case.
 *
 * <p>Two invariants hold by construction: the numerator is never greater than the denominator, and
 * the denominator is exactly the numerator plus the not-in-numerator plus the indeterminate — every
 * in-scope case is somewhere, none is dropped.
 */
public final class IndicatorEngine {

    private IndicatorEngine() {
    }

    public enum CaseClassification {
        /** In the denominator and meets the numerator. */
        NUMERATOR,
        /** In the denominator and definitively does not meet the numerator. */
        NOT_IN_NUMERATOR,
        /**
         * In the denominator, but whether it meets the numerator could not be determined. Counts as
         * not-in-numerator for the rate AND is reported, so the rate is never mistaken for complete.
         */
        INDETERMINATE,
        /** Not in the denominator for this indicator at all. */
        OUT_OF_SCOPE
    }

    /**
     * @param numerator     cases meeting the numerator
     * @param denominator   in-scope cases: numerator + notInNumerator + indeterminate
     * @param indeterminate cases in the denominator whose numerator status was unknown
     * @param rate          numerator / denominator, or null when the denominator is zero (a rate
     *                      over no cases is not 0%, it is undefined)
     * @param indeterminateRate indeterminate / denominator — the share of the figure that rests on
     *                      cases nobody could assess; a rate with a high value here is not reliable
     * @param note          plain reading of the data quality
     */
    public record Result(
            String indicatorCode,
            long numerator,
            long denominator,
            long indeterminate,
            Double rate,
            Double indeterminateRate,
            String note) {
    }

    public static Result compute(String indicatorCode, List<CaseClassification> cases) {
        long num = 0;
        long notNum = 0;
        long indet = 0;
        if (cases != null) {
            for (CaseClassification c : cases) {
                switch (c) {
                    case NUMERATOR -> num++;
                    case NOT_IN_NUMERATOR -> notNum++;
                    case INDETERMINATE -> indet++;
                    case OUT_OF_SCOPE -> { /* not in the denominator */ }
                }
            }
        }
        long denom = num + notNum + indet;

        Double rate = denom == 0 ? null : (double) num / denom;
        Double indetRate = denom == 0 ? null : (double) indet / denom;

        return new Result(indicatorCode, num, denom, indet, rate, indetRate,
                note(denom, num, indet, rate, indetRate));
    }

    private static String note(long denom, long num, long indet, Double rate, Double indetRate) {
        if (denom == 0) {
            return "No cases in the denominator — the rate is undefined, not zero.";
        }
        String base = String.format("%d of %d (%.1f%%).", num, denom, rate * 100);
        if (indet > 0) {
            return base + String.format(" But %d case(s) — %.1f%% — could not be assessed and are "
                    + "counted as not-covered rather than dropped. Read the rate with that in mind, "
                    + "not as %.1f%% of a complete assessment.", indet, indetRate * 100, rate * 100);
        }
        return base + " Every in-scope case was assessed.";
    }
}
