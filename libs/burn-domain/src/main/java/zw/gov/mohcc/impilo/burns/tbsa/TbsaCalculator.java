package zw.gov.mohcc.impilo.burns.tbsa;

import java.util.EnumMap;
import java.util.Map;

/**
 * Maps a set of burned regions onto the age-appropriate Lund–Browder chart.
 *
 * <p>Two rules are enforced here rather than left to callers, because both are easy to forget and
 * both fail in the dangerous direction:
 *
 * <ol>
 *   <li><b>Superficial burns are excluded from the resuscitation figure.</b> Counting erythema as
 *       burn inflates the fluid volume, and over-resuscitation causes compartment syndrome and
 *       pulmonary oedema. The excluded amount is reported rather than dropped, so a reviewer can
 *       see the decision.</li>
 *   <li><b>Age is mandatory.</b> There is no default band — see
 *       {@link LundBrowderChart#bandForAgeDays}.</li>
 * </ol>
 *
 * <p>Partial-region involvement is expressed as a fraction of the region, which is how a shaded
 * chart is actually read: half an anterior trunk is 0.5, not a separate region.
 */
public final class TbsaCalculator {

    private TbsaCalculator() {
    }

    /**
     * @param ageDays        completed age in days; required
     * @param burnedRegions  region → depth. A region absent from the map is unburned.
     * @param regionFraction region → fraction of that region involved (0.0–1.0). A region absent
     *                       from this map but present in {@code burnedRegions} is taken as wholly
     *                       involved (1.0), which is the common charting case.
     */
    public static TbsaAssessment calculate(Integer ageDays,
                                           Map<BodyRegion, BurnDepth> burnedRegions,
                                           Map<BodyRegion, Double> regionFraction) {
        LundBrowderChart.AgeBand band = LundBrowderChart.bandForAgeDays(ageDays);
        Map<BodyRegion, Double> contributions = new EnumMap<>(BodyRegion.class);

        double resuscitation = 0.0;
        double total = 0.0;

        for (Map.Entry<BodyRegion, BurnDepth> entry : burnedRegions.entrySet()) {
            BodyRegion region = entry.getKey();
            BurnDepth depth = entry.getValue();
            if (depth == null) {
                continue;
            }

            double fraction = fractionFor(region, regionFraction);
            double regionPercent = LundBrowderChart.percentFor(region, band) * fraction;

            total += regionPercent;
            if (depth.countsTowardResuscitation()) {
                resuscitation += regionPercent;
                contributions.put(region, round1(regionPercent));
            }
        }

        double superficial = total - resuscitation;
        return new TbsaAssessment(
                round1(resuscitation),
                round1(total),
                round1(superficial),
                band,
                Map.copyOf(contributions));
    }

    private static double fractionFor(BodyRegion region, Map<BodyRegion, Double> regionFraction) {
        if (regionFraction == null) {
            return 1.0;
        }
        Double fraction = regionFraction.get(region);
        if (fraction == null) {
            return 1.0;
        }
        if (fraction < 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException(
                    "Region involvement fraction must be between 0 and 1 for " + region + ": " + fraction);
        }
        return fraction;
    }

    /**
     * Burn charting is a clinical estimate, so a tenth of a percent is the honest precision.
     * Reporting more digits would imply a measurement nobody made.
     */
    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
