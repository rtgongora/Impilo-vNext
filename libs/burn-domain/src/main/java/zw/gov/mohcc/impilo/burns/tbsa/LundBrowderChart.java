package zw.gov.mohcc.impilo.burns.tbsa;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Lund–Browder body-surface chart, age-banded.
 *
 * <p><b>Why this exists rather than a rule of nines.</b> The rule of nines assigns a child adult
 * proportions. It does not: a newborn's head is roughly 19% of body surface against an adult's 7%,
 * and the difference is taken out of the legs. Using adult proportions on a small child therefore
 * <b>under-estimates</b> a head burn and over-estimates a leg burn, and since %TBSA is the
 * multiplier in the fluid calculation, the error propagates directly into under-resuscitation of
 * the patient least able to tolerate it. The mobile provider app shipped exactly that error until
 * 2026-07-26.
 *
 * <p><b>Bands.</b> The published chart bands at 0, 1, 5, 10, 15 years and adult. Bands are
 * inclusive of their lower bound and exclusive of the next, so a child of 4 years 11 months is in
 * the "1" band, which is the conservative reading — a younger band gives a larger head share.
 *
 * <p><b>Governance.</b> These figures are the published Lund–Browder values and are not nationally
 * adaptable; they describe human anatomy, not policy. The nationally adaptable parameters in burn
 * care are the fluid coefficient and the referral thresholds, which live elsewhere and are
 * deliberately not constants here.
 *
 * <p><b>Invariant.</b> Every band sums to exactly 100%. {@code LundBrowderChartTest} asserts it for
 * all six bands, because a chart that does not sum to 100 silently mis-scales every burn measured
 * against it.
 */
public final class LundBrowderChart {

    /** Age bands of the published chart, in whole years. */
    public enum AgeBand {
        /** Birth to under 1 year. */
        INFANT(0),
        /** 1 year to under 5. */
        YEAR_1(1),
        /** 5 years to under 10. */
        YEAR_5(5),
        /** 10 years to under 15. */
        YEAR_10(10),
        /** 15 years to under 18. */
        YEAR_15(15),
        /** 18 years and above. */
        ADULT(18);

        private final int lowerBoundYearsInclusive;

        AgeBand(int lowerBoundYearsInclusive) {
            this.lowerBoundYearsInclusive = lowerBoundYearsInclusive;
        }

        public int lowerBoundYearsInclusive() {
            return lowerBoundYearsInclusive;
        }
    }

    private LundBrowderChart() {
    }

    /**
     * Resolve the chart band for an age in days.
     *
     * @param ageDays completed age in days; must not be null and must not be negative
     * @throws IllegalArgumentException if age is unknown or negative. Deliberate: there is no
     *         defensible default band. Guessing adult under-estimates a child's head burn, and
     *         guessing infant over-estimates an adult's — so an unknown age must reach the
     *         clinician as a question, not as a silent assumption.
     */
    public static AgeBand bandForAgeDays(Integer ageDays) {
        if (ageDays == null) {
            throw new IllegalArgumentException(
                    "Age is required to map body surface: Lund-Browder proportions are age-banded and "
                            + "there is no safe default band.");
        }
        if (ageDays < 0) {
            throw new IllegalArgumentException("Age in days cannot be negative: " + ageDays);
        }
        double years = ageDays / 365.25;
        if (years < 1) return AgeBand.INFANT;
        if (years < 5) return AgeBand.YEAR_1;
        if (years < 10) return AgeBand.YEAR_5;
        if (years < 15) return AgeBand.YEAR_10;
        if (years < 18) return AgeBand.YEAR_15;
        return AgeBand.ADULT;
    }

    /**
     * Percentage of total body surface for one region in one age band.
     */
    public static double percentFor(BodyRegion region, AgeBand band) {
        return chartFor(band).get(region);
    }

    /** The full region → percent map for a band. Values sum to 100. */
    public static Map<BodyRegion, Double> chartFor(AgeBand band) {
        Map<BodyRegion, Double> chart = new EnumMap<>(BodyRegion.class);

        // ── Age-invariant regions ──────────────────────────────────────────────────────────
        chart.put(BodyRegion.NECK_ANTERIOR, 1.0);
        chart.put(BodyRegion.NECK_POSTERIOR, 1.0);
        chart.put(BodyRegion.TRUNK_ANTERIOR, 13.0);
        chart.put(BodyRegion.TRUNK_POSTERIOR, 13.0);
        chart.put(BodyRegion.BUTTOCK_LEFT, 2.5);
        chart.put(BodyRegion.BUTTOCK_RIGHT, 2.5);
        chart.put(BodyRegion.GENITALIA, 1.0);
        chart.put(BodyRegion.UPPER_ARM_LEFT, 4.0);
        chart.put(BodyRegion.UPPER_ARM_RIGHT, 4.0);
        chart.put(BodyRegion.FOREARM_LEFT, 3.0);
        chart.put(BodyRegion.FOREARM_RIGHT, 3.0);
        chart.put(BodyRegion.HAND_LEFT, 2.5);
        chart.put(BodyRegion.HAND_RIGHT, 2.5);
        chart.put(BodyRegion.FOOT_LEFT, 3.5);
        chart.put(BodyRegion.FOOT_RIGHT, 3.5);

        // ── Age-variable regions: head shrinks with age, thighs and legs grow ──────────────
        // Head is given as a single published figure and split evenly anterior/posterior, which
        // is how the chart is shaded in practice.
        double head = switch (band) {
            case INFANT -> 19.0;
            case YEAR_1 -> 17.0;
            case YEAR_5 -> 13.0;
            case YEAR_10 -> 11.0;
            case YEAR_15 -> 9.0;
            case ADULT -> 7.0;
        };
        double thighEach = switch (band) {
            case INFANT -> 5.5;
            case YEAR_1 -> 6.5;
            case YEAR_5 -> 8.0;
            case YEAR_10 -> 8.5;
            case YEAR_15 -> 9.0;
            case ADULT -> 9.5;
        };
        double lowerLegEach = switch (band) {
            case INFANT -> 5.0;
            case YEAR_1 -> 5.0;
            case YEAR_5 -> 5.5;
            case YEAR_10 -> 6.0;
            case YEAR_15 -> 6.5;
            case ADULT -> 7.0;
        };

        chart.put(BodyRegion.HEAD_ANTERIOR, head / 2.0);
        chart.put(BodyRegion.HEAD_POSTERIOR, head / 2.0);
        chart.put(BodyRegion.THIGH_LEFT, thighEach);
        chart.put(BodyRegion.THIGH_RIGHT, thighEach);
        chart.put(BodyRegion.LOWER_LEG_LEFT, lowerLegEach);
        chart.put(BodyRegion.LOWER_LEG_RIGHT, lowerLegEach);

        return chart;
    }
}
