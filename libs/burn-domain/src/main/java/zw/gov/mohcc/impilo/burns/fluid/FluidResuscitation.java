package zw.gov.mohcc.impilo.burns.fluid;

import java.time.Duration;
import java.time.Instant;

/**
 * An injury-clocked fluid resuscitation plan.
 *
 * <p><b>The clock is the whole point.</b> Parkland prescribes half the 24-hour volume in the first
 * <b>8 hours from the time of the burn</b>, not from arrival, and the remaining half over the
 * following 16. A patient who reaches the facility six hours after the injury has two hours left
 * to receive the entire first half, which means a much higher infusion rate than the naive
 * "total ÷ 24" a flat 24-hour figure invites. The provider app shipped that flat figure until
 * 2026-07-26, with no clock input at all — wrong in the under-resuscitating direction for every
 * patient who did not arrive at the moment of injury, which is nearly all of them.
 *
 * @param totalVolumeMl              full 24-hour volume from time of injury
 * @param firstHalfVolumeMl          volume due by {@code firstHalfDueAt}
 * @param secondHalfVolumeMl         volume due over the subsequent 16 hours
 * @param firstHalfDueAt             injury time + 8 hours
 * @param resuscitationEndsAt        injury time + 24 hours
 * @param remainingToFirstHalf       time left to deliver the first half as at the calculation
 *                                   instant; ZERO when the window has already closed
 * @param firstHalfWindowElapsed     true when more than 8 hours have already passed since injury,
 *                                   so the first half is overdue and cannot be delivered on
 *                                   schedule. Surfaced as a fact for the clinician, never silently
 *                                   smoothed into the remaining time.
 * @param firstHalfRateMlPerHour     rate needed to deliver the first half in the time remaining;
 *                                   null when the window has closed, because a rate implying an
 *                                   achievable schedule would be a fiction
 * @param secondHalfRateMlPerHour    rate for the second half over its 16 hours
 * @param coefficientMlPerKgPerPercent the coefficient used, echoed because it is a nationally
 *                                   adaptable parameter and a volume without its coefficient
 *                                   cannot be checked
 * @param maintenanceIncluded        whether paediatric maintenance fluid is included in the total
 */
public record FluidResuscitation(
        double totalVolumeMl,
        double firstHalfVolumeMl,
        double secondHalfVolumeMl,
        Instant firstHalfDueAt,
        Instant resuscitationEndsAt,
        Duration remainingToFirstHalf,
        boolean firstHalfWindowElapsed,
        Double firstHalfRateMlPerHour,
        double secondHalfRateMlPerHour,
        double coefficientMlPerKgPerPercent,
        boolean maintenanceIncluded) {

    /**
     * A short human-readable statement of the schedule, phrased so the injury clock is explicit.
     * Deliberately not a template a caller can drop the clock from.
     */
    public String scheduleSummary() {
        if (firstHalfWindowElapsed) {
            return String.format(
                    "%.0f ml total over 24h from injury. The first-half window (%.0f ml by 8h post-burn) "
                            + "has already closed — give the deficit now under senior review and reassess "
                            + "against urine output.",
                    totalVolumeMl, firstHalfVolumeMl);
        }
        return String.format(
                "%.0f ml total over 24h from injury: %.0f ml by 8h post-burn (%.0f ml/h for the %d h %d min "
                        + "remaining), then %.0f ml over the next 16h (%.0f ml/h).",
                totalVolumeMl, firstHalfVolumeMl,
                firstHalfRateMlPerHour == null ? 0 : firstHalfRateMlPerHour,
                remainingToFirstHalf.toHours(), remainingToFirstHalf.toMinutesPart(),
                secondHalfVolumeMl, secondHalfRateMlPerHour);
    }
}
