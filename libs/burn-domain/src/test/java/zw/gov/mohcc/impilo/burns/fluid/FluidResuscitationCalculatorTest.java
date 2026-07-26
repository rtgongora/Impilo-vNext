package zw.gov.mohcc.impilo.burns.fluid;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluidResuscitationCalculatorTest {

    private static final Instant INJURY = Instant.parse("2026-07-26T08:00:00Z");

    @Test
    void computesTheClassicalParklandTotal() {
        FluidResuscitation plan = FluidResuscitationCalculator.calculate(
                70.0, 30.0, INJURY, INJURY,
                FluidResuscitationCalculator.PARKLAND_COEFFICIENT, null);

        // 4 x 70 x 30 = 8400 ml
        assertEquals(8400.0, plan.totalVolumeMl(), 0.01);
        assertEquals(4200.0, plan.firstHalfVolumeMl(), 0.01);
        assertEquals(4200.0, plan.secondHalfVolumeMl(), 0.01);
    }

    /**
     * The defect the mobile app shipped. Presenting at the moment of injury, the first half is
     * spread over the full 8 hours: 525 ml/h. Presenting six hours late, the SAME first half has
     * two hours left: 2100 ml/h — four times the rate. A flat 24-hour total invites the clinician
     * to infer 350 ml/h and under-resuscitate by a wide margin.
     */
    @Test
    void aLatePresentationCompressesTheFirstHalfIntoTheTimeThatRemains() {
        FluidResuscitation onTime = FluidResuscitationCalculator.calculate(
                70.0, 30.0, INJURY, INJURY, 4.0, null);
        FluidResuscitation sixHoursLate = FluidResuscitationCalculator.calculate(
                70.0, 30.0, INJURY, INJURY.plus(Duration.ofHours(6)), 4.0, null);

        assertEquals(525.0, onTime.firstHalfRateMlPerHour(), 0.5);
        assertEquals(2100.0, sixHoursLate.firstHalfRateMlPerHour(), 0.5);

        // Same total, same first-half volume — only the schedule differs.
        assertEquals(onTime.totalVolumeMl(), sixHoursLate.totalVolumeMl(), 0.01);
        assertEquals(onTime.firstHalfVolumeMl(), sixHoursLate.firstHalfVolumeMl(), 0.01);
        assertTrue(sixHoursLate.firstHalfRateMlPerHour() > onTime.firstHalfRateMlPerHour() * 3);
    }

    /**
     * Past 8 hours the window has closed. The plan must say so rather than quietly producing a
     * rate, because any rate at that point implies a schedule that can still be met.
     */
    @Test
    void anElapsedFirstHalfWindowIsReportedRatherThanSmoothedAway() {
        FluidResuscitation plan = FluidResuscitationCalculator.calculate(
                70.0, 30.0, INJURY, INJURY.plus(Duration.ofHours(9)), 4.0, null);

        assertTrue(plan.firstHalfWindowElapsed());
        assertNull(plan.firstHalfRateMlPerHour(),
                "a rate would imply the first half can still be delivered on schedule");
        assertEquals(Duration.ZERO, plan.remainingToFirstHalf());
        assertTrue(plan.scheduleSummary().contains("already closed"));
    }

    @Test
    void theScheduleSummaryAlwaysStatesTheInjuryClock() {
        String onTime = FluidResuscitationCalculator
                .calculate(70.0, 30.0, INJURY, INJURY, 4.0, null).scheduleSummary();
        assertTrue(onTime.contains("from injury"), onTime);
        assertTrue(onTime.contains("post-burn"), onTime);
    }

    /**
     * The coefficient is national policy, not a constant. A volume that does not carry the
     * coefficient that produced it cannot be checked by anyone reading the record later.
     */
    @Test
    void theCoefficientIsAParameterAndIsEchoedInTheResult() {
        FluidResuscitation modern = FluidResuscitationCalculator.calculate(
                70.0, 30.0, INJURY, INJURY, 3.0, null);

        assertEquals(6300.0, modern.totalVolumeMl(), 0.01);
        assertEquals(3.0, modern.coefficientMlPerKgPerPercent(), 0.001);
    }

    /**
     * Paediatric maintenance runs evenly across 24 hours and is not part of the half-in-8-hours
     * burn volume. Folding it into the first half would over-deliver early to the patient least
     * able to tolerate it.
     */
    @Test
    void paediatricMaintenanceIsAddedButNotFrontLoaded() {
        FluidResuscitation plan = FluidResuscitationCalculator.calculate(
                20.0, 25.0, INJURY, INJURY, 3.0, 60.0);

        double burnVolume = 3.0 * 20.0 * 25.0;      // 1500
        double maintenance = 60.0 * 24.0;            // 1440
        assertEquals(burnVolume + maintenance, plan.totalVolumeMl(), 0.01);
        assertEquals(burnVolume / 2.0, plan.firstHalfVolumeMl(), 0.01);
        assertEquals(burnVolume / 2.0 + maintenance, plan.secondHalfVolumeMl(), 0.01);
        assertTrue(plan.maintenanceIncluded());
    }

    @Test
    void missingInjuryTimeIsRefusedRatherThanAssumed() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                FluidResuscitationCalculator.calculate(70.0, 30.0, null, INJURY, 4.0, null));
        assertTrue(e.getMessage().contains("measured from the burn"));
    }

    @Test
    void missingOrNonPositiveWeightIsRefused() {
        assertThrows(IllegalArgumentException.class, () ->
                FluidResuscitationCalculator.calculate(null, 30.0, INJURY, INJURY, 4.0, null));
        assertThrows(IllegalArgumentException.class, () ->
                FluidResuscitationCalculator.calculate(0.0, 30.0, INJURY, INJURY, 4.0, null));
    }

    @Test
    void deadlinesAreAnchoredToInjuryNotToNow() {
        FluidResuscitation plan = FluidResuscitationCalculator.calculate(
                70.0, 30.0, INJURY, INJURY.plus(Duration.ofHours(3)), 4.0, null);

        assertEquals(INJURY.plus(Duration.ofHours(8)), plan.firstHalfDueAt());
        assertEquals(INJURY.plus(Duration.ofHours(24)), plan.resuscitationEndsAt());
        assertEquals(Duration.ofHours(5), plan.remainingToFirstHalf());
        assertFalse(plan.firstHalfWindowElapsed());
        assertNotNull(plan.firstHalfRateMlPerHour());
    }
}
