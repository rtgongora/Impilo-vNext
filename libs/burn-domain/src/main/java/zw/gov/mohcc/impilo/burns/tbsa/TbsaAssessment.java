package zw.gov.mohcc.impilo.burns.tbsa;

import java.util.Map;

/**
 * The result of mapping a burn onto the Lund–Browder chart.
 *
 * @param resuscitationTbsaPercent %TBSA counting only depths that drive fluid resuscitation —
 *        this is the number that goes into the fluid calculation
 * @param totalMappedTbsaPercent   %TBSA counting every mapped region including superficial burns —
 *        clinically useful for describing the injury, and deliberately NOT the resuscitation input
 * @param superficialTbsaPercent   the difference, reported explicitly so a reviewer can see what
 *        was excluded rather than having to infer it from two totals
 * @param ageBand                  the chart band used, echoed so a retrospective reader knows which
 *        proportions produced the number
 * @param regionPercentages        per-region contribution to the resuscitation figure
 */
public record TbsaAssessment(
        double resuscitationTbsaPercent,
        double totalMappedTbsaPercent,
        double superficialTbsaPercent,
        LundBrowderChart.AgeBand ageBand,
        Map<BodyRegion, Double> regionPercentages) {

    /**
     * Whether this burn meets the conventional threshold at which formal fluid resuscitation is
     * started. The thresholds themselves are national policy — 15% in adults and 10% in children
     * is the common convention — so this is a convenience for display, not an authorisation.
     * The decision to resuscitate belongs to the clinician and to national protocol content, never
     * to this library.
     */
    public boolean meetsConventionalResuscitationThreshold() {
        double threshold = ageBand == LundBrowderChart.AgeBand.ADULT ? 15.0 : 10.0;
        return resuscitationTbsaPercent >= threshold;
    }
}
