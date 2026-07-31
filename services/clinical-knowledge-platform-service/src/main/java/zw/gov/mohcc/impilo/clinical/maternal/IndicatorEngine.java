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
 *
 * <p>It carries two families of figure. {@link #compute} handles coverage-shaped indicators, a
 * fraction of one case set. {@link #computeSevereMaternalOutcomes} handles the WHO near-miss ratio and
 * mortality index, which relate two different counts and where — as documented there — the
 * indeterminate law has to be applied by its purpose rather than its letter to stay honest.
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

    // ── Severe maternal outcomes: the near-miss-to-death ratio and the mortality index ─────────────

    /**
     * A woman's severe-outcome status for the period being reported (WHO near-miss approach, 2011).
     *
     * <p>{@link #NEAR_MISS_INDETERMINATE} exists because a near-miss classification can be unresolved
     * — a criterion nobody measured leaves {@code MaternalNearMissService} reporting INDETERMINATE
     * rather than not-met. That case is a real woman with a real outcome, and she must not vanish.
     */
    public enum OutcomeCase {
        /** Survived, and confirmed to have met the organ-dysfunction criteria. */
        NEAR_MISS,
        /** Died of a maternal cause. */
        MATERNAL_DEATH,
        /** Survived, but whether she met the criteria could not be determined. */
        NEAR_MISS_INDETERMINATE,
        /** Survived, and confirmed not to have met the criteria. */
        NOT_SEVERE
    }

    /**
     * @param nearMiss              confirmed near-misses
     * @param maternalDeaths        maternal deaths
     * @param indeterminate         survivors whose near-miss status is unresolved
     * @param severeMaternalOutcomes confirmed near-misses + deaths
     * @param nearMissToDeathRatio  confirmed near-misses per death, or null when there were no deaths
     *                              (a ratio over zero deaths is undefined, not infinite and not good)
     * @param nearMissToDeathRatioUpperBound the same ratio if every indeterminate case turned out to
     *                              be a near-miss — the most favourable reading the data permits
     * @param mortalityIndex        deaths / (near-misses + deaths): the share of women with a severe
     *                              outcome who died. HIGHER IS WORSE. Computed over confirmed cases,
     *                              so it is the least favourable reading, never the most.
     * @param mortalityIndexLowerBound the index if every indeterminate case turned out to be a
     *                              near-miss — the most favourable reading the data permits
     * @param note                  plain reading, including which direction the missing data moves the
     *                              figure
     */
    public record SevereOutcomeResult(
            long nearMiss,
            long maternalDeaths,
            long indeterminate,
            long severeMaternalOutcomes,
            Double nearMissToDeathRatio,
            Double nearMissToDeathRatioUpperBound,
            Double mortalityIndex,
            Double mortalityIndexLowerBound,
            String note) {
    }

    /**
     * Computes the maternal near-miss-to-death ratio and the mortality index.
     *
     * <p><b>Why this does not apply "indeterminate stays in the denominator" by rote.</b> That law
     * exists to stop missing data flattering a figure, and for coverage the two coincide: an
     * unassessable case kept in the denominator and out of the numerator pushes coverage DOWN, which
     * is the safe direction. Here they come apart. The mortality index's denominator is
     * near-misses + deaths, so counting an unresolved survivor into it ENLARGES the denominator under
     * a fixed numerator of deaths and pushes the index DOWN — and a lower mortality index reads as
     * better care. Applying the letter of the law would let every unmeasured creatinine improve a
     * facility's apparent quality of care. The same is true of the ratio, where an unresolved survivor
     * would raise the near-misses-per-death figure.
     *
     * <p>So the law is honoured where it actually bites — no case is dropped, and the indeterminate
     * count travels with the result — while the point estimates are computed over CONFIRMED cases
     * only, making the reported index the least favourable reading rather than the most. The bounds
     * then say exactly how much the unresolved cases could move it. The width between a point estimate
     * and its bound is the cost of not recording an observation, stated rather than hidden.
     *
     * <p><b>Zero deaths is not a perfect score.</b> The ratio is undefined with no deaths, so it is
     * null rather than infinity, and the note refuses to read a small denominator as evidence of
     * safety. Both figures over a handful of women describe the handful, not the service.
     */
    public static SevereOutcomeResult computeSevereMaternalOutcomes(List<OutcomeCase> cases) {
        long nearMiss = 0;
        long deaths = 0;
        long indet = 0;
        if (cases != null) {
            for (OutcomeCase c : cases) {
                switch (c) {
                    case NEAR_MISS -> nearMiss++;
                    case MATERNAL_DEATH -> deaths++;
                    case NEAR_MISS_INDETERMINATE -> indet++;
                    case NOT_SEVERE -> { /* not a severe maternal outcome */ }
                }
            }
        }
        long smo = nearMiss + deaths;

        Double ratio = deaths == 0 ? null : (double) nearMiss / deaths;
        Double ratioUpper = deaths == 0 ? null : (double) (nearMiss + indet) / deaths;
        Double index = smo == 0 ? null : (double) deaths / smo;
        Double indexLower = (smo + indet) == 0 ? null : (double) deaths / (smo + indet);

        return new SevereOutcomeResult(nearMiss, deaths, indet, smo, ratio, ratioUpper, index,
                indexLower, severeOutcomeNote(nearMiss, deaths, indet, smo, ratio, index, indexLower));
    }

    private static String severeOutcomeNote(long nearMiss, long deaths, long indet, long smo,
                                            Double ratio, Double index, Double indexLower) {
        StringBuilder s = new StringBuilder();
        if (smo == 0 && indet == 0) {
            return "No severe maternal outcomes recorded — both figures are undefined, not zero. "
                    + "That is a statement about this record set, not about the safety of this service.";
        }

        s.append(String.format("%d near-miss(es) and %d maternal death(s).", nearMiss, deaths));

        if (deaths == 0) {
            s.append(" With no deaths the near-miss-to-death ratio is undefined rather than infinite,"
                    + " and no death in a period this size is not evidence that none will occur.");
        } else {
            s.append(String.format(" Ratio %.1f near-miss(es) per death; mortality index %.1f%%"
                    + " (higher is worse).", ratio, index * 100));
        }

        if (indet > 0) {
            s.append(String.format(" %d survivor(s) could not be classified, so they are excluded from"
                    + " the point estimates rather than counted as near-misses — counting them would"
                    + " lower the mortality index and flatter the service.", indet));
            if (index != null && indexLower != null) {
                s.append(String.format(" If all %d proved to be near-misses the index would fall to"
                        + " %.1f%%, so the true value lies between %.1f%% and %.1f%%.",
                        indet, indexLower * 100, indexLower * 100, index * 100));
            }
            s.append(" Closing that gap is a recording problem, not an analysis one.");
        }
        return s.toString();
    }
}
