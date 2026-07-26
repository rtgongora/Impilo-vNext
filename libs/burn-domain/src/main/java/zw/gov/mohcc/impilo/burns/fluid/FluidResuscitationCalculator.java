package zw.gov.mohcc.impilo.burns.fluid;

import java.time.Duration;
import java.time.Instant;

/**
 * Burn fluid resuscitation, clocked from the time of injury.
 *
 * <p><b>The coefficient is a parameter, not a constant.</b> 4 ml/kg/%TBSA is the classical Parkland
 * figure; 2–3 ml/kg/%TBSA is now widely recommended for adults, and paediatric practice commonly
 * uses 3 ml/kg/%TBSA <i>plus</i> maintenance fluid. Which figure applies is national protocol, so
 * it is supplied by the caller from governed content and echoed in the result. Baking a number in
 * here would make a policy change a code deployment, and would hide which figure produced a volume.
 *
 * <p>Everything this class returns is an <b>estimate for the first hours only</b>. Resuscitation is
 * titrated to urine output and perfusion, not run to a formula, and no output of this class is an
 * instruction to infuse. That is why the result carries a schedule rather than a single number: a
 * lone total invites an even infusion, which is the failure the app it replaces actually had.
 */
public final class FluidResuscitationCalculator {

    /** Classical Parkland. Provided as a named constant so callers do not spell 4.0 inline. */
    public static final double PARKLAND_COEFFICIENT = 4.0;

    /** First-half window: 8 hours from the burn. */
    public static final Duration FIRST_HALF_WINDOW = Duration.ofHours(8);

    /** Full resuscitation period: 24 hours from the burn. */
    public static final Duration RESUSCITATION_PERIOD = Duration.ofHours(24);

    private FluidResuscitationCalculator() {
    }

    /**
     * @param weightKg                     patient weight; required and must be positive
     * @param resuscitationTbsaPercent     %TBSA excluding superficial burns — the figure from
     *                                     {@code TbsaAssessment.resuscitationTbsaPercent()}
     * @param injuryTime                   when the burn occurred; required
     * @param calculationTime              "now" — passed in rather than read from the clock so the
     *                                     result is reproducible and testable
     * @param coefficientMlPerKgPerPercent from national protocol content
     * @param maintenanceMlPerHour         paediatric maintenance to add across the 24 hours, or
     *                                     null for none. Added to the total and spread evenly,
     *                                     because maintenance is not part of the half-in-8-hours
     *                                     schedule.
     * @throws IllegalArgumentException on missing or non-positive inputs. There is no defaulting:
     *                                  a fluid volume computed from a guessed weight or a guessed
     *                                  injury time is a plausible number that nobody can check.
     */
    public static FluidResuscitation calculate(Double weightKg,
                                               Double resuscitationTbsaPercent,
                                               Instant injuryTime,
                                               Instant calculationTime,
                                               double coefficientMlPerKgPerPercent,
                                               Double maintenanceMlPerHour) {
        require(weightKg != null && weightKg > 0, "Weight in kg is required and must be positive.");
        require(resuscitationTbsaPercent != null && resuscitationTbsaPercent >= 0,
                "Resuscitation %TBSA is required and cannot be negative.");
        require(injuryTime != null,
                "Time of injury is required: the resuscitation schedule is measured from the burn, "
                        + "not from arrival, so a volume without an injury time cannot be scheduled.");
        require(calculationTime != null, "Calculation time is required.");
        require(coefficientMlPerKgPerPercent > 0,
                "Fluid coefficient must be positive and comes from national protocol content.");

        double burnVolume = coefficientMlPerKgPerPercent * weightKg * resuscitationTbsaPercent;
        double maintenance24h = maintenanceMlPerHour == null ? 0.0 : maintenanceMlPerHour * 24.0;
        double total = burnVolume + maintenance24h;

        // The half-in-8-hours rule applies to the BURN volume. Maintenance runs evenly across the
        // 24 hours and is not front-loaded, so folding it into the first half would over-deliver
        // early.
        double firstHalf = burnVolume / 2.0;
        double secondHalf = burnVolume / 2.0 + maintenance24h;

        Instant firstHalfDueAt = injuryTime.plus(FIRST_HALF_WINDOW);
        Instant endsAt = injuryTime.plus(RESUSCITATION_PERIOD);

        Duration remaining = Duration.between(calculationTime, firstHalfDueAt);
        boolean elapsed = remaining.isNegative() || remaining.isZero();
        Duration remainingClamped = elapsed ? Duration.ZERO : remaining;

        Double firstHalfRate = null;
        if (!elapsed) {
            double hoursLeft = remainingClamped.toMinutes() / 60.0;
            firstHalfRate = round(firstHalf / hoursLeft);
        }

        double secondHalfRate = round(secondHalf / 16.0);

        return new FluidResuscitation(
                round(total),
                round(firstHalf),
                round(secondHalf),
                firstHalfDueAt,
                endsAt,
                remainingClamped,
                elapsed,
                firstHalfRate,
                secondHalfRate,
                coefficientMlPerKgPerPercent,
                maintenanceMlPerHour != null);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
