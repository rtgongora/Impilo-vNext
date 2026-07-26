package zw.gov.mohcc.impilo.burns.tbsa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LundBrowderChartTest {

    /**
     * The invariant that makes every other number trustworthy. A chart that does not sum to 100
     * silently mis-scales every burn measured against it, and the error is invisible at the point
     * of use because each individual region still looks reasonable.
     */
    @TestFactory
    @DisplayName("every age band sums to exactly 100% of body surface")
    List<DynamicTest> everyBandSumsTo100() {
        return Arrays.stream(LundBrowderChart.AgeBand.values())
                .map(band -> DynamicTest.dynamicTest(band.name(), () -> {
                    double sum = LundBrowderChart.chartFor(band).values().stream()
                            .mapToDouble(Double::doubleValue).sum();
                    assertEquals(100.0, sum, 0.001,
                            band + " chart sums to " + sum + "%, not 100%");
                }))
                .toList();
    }

    @TestFactory
    @DisplayName("every band assigns a percentage to every region")
    List<DynamicTest> everyBandCoversEveryRegion() {
        return Arrays.stream(LundBrowderChart.AgeBand.values())
                .map(band -> DynamicTest.dynamicTest(band.name(), () -> {
                    Map<BodyRegion, Double> chart = LundBrowderChart.chartFor(band);
                    for (BodyRegion region : BodyRegion.values()) {
                        assertTrue(chart.containsKey(region),
                                band + " has no percentage for " + region
                                        + " — an unmapped region silently contributes zero");
                    }
                }))
                .toList();
    }

    /**
     * This is the defect the mobile app shipped: adult proportions applied to a child. The head is
     * where it bites hardest, and it bites in the under-estimating direction.
     */
    @Test
    void aninfantHeadIsWorthNearlyThreeTimesAnAdultHead() {
        double infantHead = LundBrowderChart.percentFor(BodyRegion.HEAD_ANTERIOR, LundBrowderChart.AgeBand.INFANT)
                + LundBrowderChart.percentFor(BodyRegion.HEAD_POSTERIOR, LundBrowderChart.AgeBand.INFANT);
        double adultHead = LundBrowderChart.percentFor(BodyRegion.HEAD_ANTERIOR, LundBrowderChart.AgeBand.ADULT)
                + LundBrowderChart.percentFor(BodyRegion.HEAD_POSTERIOR, LundBrowderChart.AgeBand.ADULT);

        assertEquals(19.0, infantHead, 0.001);
        assertEquals(7.0, adultHead, 0.001);
        assertTrue(infantHead > adultHead * 2.5,
                "using an adult chart on an infant under-estimates a head burn by 12 points of TBSA");
    }

    @Test
    void headShrinksAndLegsGrowMonotonicallyWithAge() {
        double previousHead = Double.MAX_VALUE;
        double previousLeg = 0.0;
        for (LundBrowderChart.AgeBand band : LundBrowderChart.AgeBand.values()) {
            double head = LundBrowderChart.percentFor(BodyRegion.HEAD_ANTERIOR, band) * 2;
            double leg = LundBrowderChart.percentFor(BodyRegion.THIGH_LEFT, band)
                    + LundBrowderChart.percentFor(BodyRegion.LOWER_LEG_LEFT, band);
            assertTrue(head < previousHead, "head share must shrink with age, broke at " + band);
            assertTrue(leg > previousLeg, "leg share must grow with age, broke at " + band);
            previousHead = head;
            previousLeg = leg;
        }
    }

    @Test
    void ageBandBoundariesUseTheYoungerBandUpToTheBoundary() {
        // 4y364d is still the "1" band; 5y is the "5" band. The younger band gives a larger head
        // share, which is the conservative direction for a head burn.
        assertEquals(LundBrowderChart.AgeBand.YEAR_1, LundBrowderChart.bandForAgeDays(1825 - 1));
        assertEquals(LundBrowderChart.AgeBand.YEAR_5, LundBrowderChart.bandForAgeDays(1827));
        assertEquals(LundBrowderChart.AgeBand.INFANT, LundBrowderChart.bandForAgeDays(0));
        assertEquals(LundBrowderChart.AgeBand.ADULT, LundBrowderChart.bandForAgeDays(365 * 40));
    }

    /**
     * There is no safe default band, so an unknown age must raise rather than assume. Guessing
     * adult under-estimates a child's head burn; guessing infant over-estimates an adult's.
     */
    @Test
    void unknownAgeIsRefusedRatherThanDefaulted() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> LundBrowderChart.bandForAgeDays(null));
        assertTrue(e.getMessage().contains("no safe default"));
        assertThrows(IllegalArgumentException.class, () -> LundBrowderChart.bandForAgeDays(-1));
    }
}
