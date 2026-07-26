package zw.gov.mohcc.impilo.burns.tbsa;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TbsaCalculatorTest {

    private static final int ADULT_DAYS = 365 * 30;
    private static final int INFANT_DAYS = 180;

    private static Map<BodyRegion, BurnDepth> burns(Object... pairs) {
        Map<BodyRegion, BurnDepth> map = new EnumMap<>(BodyRegion.class);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((BodyRegion) pairs[i], (BurnDepth) pairs[i + 1]);
        }
        return map;
    }

    /**
     * The rule that stops over-resuscitation. Erythema is part of the injury description but not
     * of the fluid calculation, and the excluded amount is reported rather than dropped.
     */
    @Test
    void superficialBurnsAreExcludedFromTheResuscitationFigureButStillReported() {
        TbsaAssessment a = TbsaCalculator.calculate(ADULT_DAYS, burns(
                BodyRegion.TRUNK_ANTERIOR, BurnDepth.DEEP_PARTIAL,   // 13
                BodyRegion.TRUNK_POSTERIOR, BurnDepth.SUPERFICIAL    // 13, excluded
        ), null);

        assertEquals(13.0, a.resuscitationTbsaPercent(), 0.01);
        assertEquals(26.0, a.totalMappedTbsaPercent(), 0.01);
        assertEquals(13.0, a.superficialTbsaPercent(), 0.01);
    }

    /**
     * The same burn on the same body gives a materially different %TBSA depending on age, which is
     * the entire reason this chart is age-banded.
     */
    @Test
    void theSameHeadBurnIsWorthFarMoreOnAnInfantThanAnAdult() {
        Map<BodyRegion, BurnDepth> wholeHead = burns(
                BodyRegion.HEAD_ANTERIOR, BurnDepth.DEEP_PARTIAL,
                BodyRegion.HEAD_POSTERIOR, BurnDepth.DEEP_PARTIAL);

        double infant = TbsaCalculator.calculate(INFANT_DAYS, wholeHead, null).resuscitationTbsaPercent();
        double adult = TbsaCalculator.calculate(ADULT_DAYS, wholeHead, null).resuscitationTbsaPercent();

        assertEquals(19.0, infant, 0.01);
        assertEquals(7.0, adult, 0.01);
    }

    @Test
    void partialRegionInvolvementScalesTheRegionPercentage() {
        Map<BodyRegion, Double> half = new EnumMap<>(BodyRegion.class);
        half.put(BodyRegion.TRUNK_ANTERIOR, 0.5);

        TbsaAssessment a = TbsaCalculator.calculate(ADULT_DAYS,
                burns(BodyRegion.TRUNK_ANTERIOR, BurnDepth.FULL_THICKNESS), half);

        assertEquals(6.5, a.resuscitationTbsaPercent(), 0.01);
    }

    @Test
    void aRegionWithNoStatedFractionIsTakenAsWhollyInvolved() {
        TbsaAssessment a = TbsaCalculator.calculate(ADULT_DAYS,
                burns(BodyRegion.HAND_LEFT, BurnDepth.DEEP_PARTIAL), Map.of());
        assertEquals(2.5, a.resuscitationTbsaPercent(), 0.01);
    }

    @Test
    void anOutOfRangeFractionIsRefused() {
        Map<BodyRegion, Double> bad = new EnumMap<>(BodyRegion.class);
        bad.put(BodyRegion.TRUNK_ANTERIOR, 1.5);
        assertThrows(IllegalArgumentException.class, () -> TbsaCalculator.calculate(
                ADULT_DAYS, burns(BodyRegion.TRUNK_ANTERIOR, BurnDepth.DEEP_PARTIAL), bad));
    }

    @Test
    void unknownAgeIsRefusedRatherThanDefaultedToAdult() {
        assertThrows(IllegalArgumentException.class, () -> TbsaCalculator.calculate(
                null, burns(BodyRegion.TRUNK_ANTERIOR, BurnDepth.DEEP_PARTIAL), null));
    }

    @Test
    void wholeBodyDeepBurnIsOneHundredPercent() {
        Map<BodyRegion, BurnDepth> all = new EnumMap<>(BodyRegion.class);
        for (BodyRegion region : BodyRegion.values()) {
            all.put(region, BurnDepth.FULL_THICKNESS);
        }
        assertEquals(100.0, TbsaCalculator.calculate(ADULT_DAYS, all, null).resuscitationTbsaPercent(), 0.01);
    }

    /**
     * The threshold is a convenience for display, and it is age-aware: 10% in children against 15%
     * in adults. A 12% burn crosses it for a child and does not for an adult.
     */
    @Test
    void theConventionalThresholdIsAgeAware() {
        Map<BodyRegion, BurnDepth> b = burns(
                BodyRegion.TRUNK_ANTERIOR, BurnDepth.DEEP_PARTIAL); // 13% at every age

        assertTrue(TbsaCalculator.calculate(INFANT_DAYS, b, null)
                .meetsConventionalResuscitationThreshold());
        assertFalse(TbsaCalculator.calculate(ADULT_DAYS, b, null)
                .meetsConventionalResuscitationThreshold());
    }
}
