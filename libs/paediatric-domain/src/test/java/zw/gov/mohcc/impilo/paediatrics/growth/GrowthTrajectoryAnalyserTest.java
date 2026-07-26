package zw.gov.mohcc.impilo.paediatrics.growth;

import org.junit.jupiter.api.Test;

import zw.gov.mohcc.impilo.paediatrics.growth.GrowthTrajectoryAnalyser.TrajectoryPoint;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthTrajectoryAnalyserTest {

    @Test
    void detectsFalteringWhileWeightIsStillIncreasing() {
        // The exemplar case: weight rises, but the child loses ground against the standard.
        var assessment = GrowthTrajectoryAnalyser.analyse(List.of(
                new TrajectoryPoint(180, BigDecimal.valueOf(7.2d), BigDecimal.valueOf(-1.4d)),
                new TrajectoryPoint(300, BigDecimal.valueOf(7.9d), BigDecimal.valueOf(-2.2d))));

        assertTrue(assessment.faltering());
        assertFalse(assessment.weightLossSinceLast());
        assertEquals(new BigDecimal("-0.80"), assessment.zScoreChange());
        assertTrue(assessment.observations().stream().anyMatch(o -> o.contains("-1.4") && o.contains("-2.2")));
        assertTrue(assessment.observations().stream()
                .anyMatch(o -> o.contains("not fast enough to keep pace")));
    }

    @Test
    void steadyTrajectoryIsNotFlaggedAsFaltering() {
        var assessment = GrowthTrajectoryAnalyser.analyse(List.of(
                new TrajectoryPoint(180, BigDecimal.valueOf(7.2d), BigDecimal.valueOf(-0.5d)),
                new TrajectoryPoint(300, BigDecimal.valueOf(8.4d), BigDecimal.valueOf(-0.4d))));

        assertFalse(assessment.faltering());
        assertFalse(assessment.weightLossSinceLast());
    }

    @Test
    void absoluteWeightLossIsReportedSeparatelyFromZScoreDrift() {
        var assessment = GrowthTrajectoryAnalyser.analyse(List.of(
                new TrajectoryPoint(300, BigDecimal.valueOf(8.4d), BigDecimal.valueOf(-0.4d)),
                new TrajectoryPoint(330, BigDecimal.valueOf(8.1d), BigDecimal.valueOf(-0.7d))));

        assertTrue(assessment.weightLossSinceLast());
        assertFalse(assessment.faltering());
        assertTrue(assessment.observations().stream().anyMatch(o -> o.contains("8.4") && o.contains("8.1")));
    }

    @Test
    void impossibleZScoreIsRaisedAsDataQualityNotAsCriticalIllness() {
        var assessment = GrowthTrajectoryAnalyser.analyse(List.of(
                new TrajectoryPoint(180, BigDecimal.valueOf(7.2d), BigDecimal.valueOf(-0.5d)),
                new TrajectoryPoint(300, BigDecimal.valueOf(7.6d), BigDecimal.valueOf(-8.4d))));

        assertTrue(assessment.dataQualityConcerns().stream().anyMatch(c -> c.contains("Re-measure")));
    }

    @Test
    void suddenWeightJumpAsksForConfirmationBeforeAction() {
        var assessment = GrowthTrajectoryAnalyser.analyse(List.of(
                new TrajectoryPoint(300, BigDecimal.valueOf(8.0d), BigDecimal.valueOf(-0.4d)),
                new TrajectoryPoint(330, BigDecimal.valueOf(14.0d), BigDecimal.valueOf(2.9d))));

        assertTrue(assessment.dataQualityConcerns().stream()
                .anyMatch(c -> c.contains("larger change than is usually possible")));
    }

    @Test
    void aSingleMeasurementCannotEstablishATrajectory() {
        var assessment = GrowthTrajectoryAnalyser.analyse(List.of(
                new TrajectoryPoint(180, BigDecimal.valueOf(7.2d), BigDecimal.valueOf(-2.9d))));

        assertFalse(assessment.faltering());
        assertTrue(assessment.observations().stream().anyMatch(o -> o.contains("at least two contacts")));
    }

    @Test
    void emptyHistorySaysSoRatherThanClaimingHealthyGrowth() {
        var assessment = GrowthTrajectoryAnalyser.analyse(List.of());

        assertFalse(assessment.faltering());
        assertTrue(assessment.observations().get(0).contains("No previous measurements"));
    }

    @Test
    void pointsArrivingOutOfOrderAreStillReadOldestToNewest() {
        var assessment = GrowthTrajectoryAnalyser.analyse(List.of(
                new TrajectoryPoint(300, BigDecimal.valueOf(7.9d), BigDecimal.valueOf(-2.2d)),
                new TrajectoryPoint(180, BigDecimal.valueOf(7.2d), BigDecimal.valueOf(-1.4d))));

        assertEquals(new BigDecimal("-0.80"), assessment.zScoreChange());
        assertTrue(assessment.faltering());
    }
}
