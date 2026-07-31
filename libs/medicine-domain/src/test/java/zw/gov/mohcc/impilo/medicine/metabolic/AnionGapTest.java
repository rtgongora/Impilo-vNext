package zw.gov.mohcc.impilo.medicine.metabolic;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * A raised anion gap is the finding that separates a metabolic acidosis needing urgent explanation
 * from one that does not. Each test names the harm the assertion stands between a patient and.
 */
class AnionGapTest {

    @Test
    void theStandardElectrolytePanelGivesThePublishedGap() {
        // Na 140, Cl 100, HCO₃ 24 without potassium: 140 − 100 − 24 = 16.
        var result = AnionGap.of(BigDecimal.valueOf(140d), BigDecimal.valueOf(100d),
                BigDecimal.valueOf(24d), null, null);

        assertThat(result.computed()).isTrue();
        assertThat(result.anionGapMmolPerL().doubleValue()).isCloseTo(16d, within(0.05d));
        assertThat(result.includesPotassium()).isFalse();
        assertThat(result.contentVersion()).isEqualTo(AnionGap.CONTENT_VERSION);
    }

    @Test
    void includingPotassiumRaisesTheGapAndTheResultSaysSo() {
        // The two conventions differ by roughly four, which is most of the width of the reference
        // range. A gap quoted against the wrong convention reads as raised when it is not.
        var without = AnionGap.of(BigDecimal.valueOf(140d), BigDecimal.valueOf(100d),
                BigDecimal.valueOf(24d), null, null);
        var with = AnionGap.of(BigDecimal.valueOf(140d), BigDecimal.valueOf(100d),
                BigDecimal.valueOf(24d), BigDecimal.valueOf(4d), null);

        assertThat(with.anionGapMmolPerL().doubleValue())
                .isCloseTo(without.anionGapMmolPerL().doubleValue() + 4d, within(0.05d));
        assertThat(with.includesPotassium()).isTrue();
        assertThat(without.includesPotassium()).isFalse();
    }

    @Test
    void hypoalbuminaemiaHidesARaisedGapUntilTheCorrectionIsApplied() {
        // The case that makes the correction matter. Na 140, Cl 108, HCO₃ 20 gives a gap of 12,
        // which reads as normal. With an albumin of 20 g/L the corrected gap is 17 — a raised gap,
        // and a metabolic acidosis that needs an explanation nobody has gone looking for.
        var uncorrected = AnionGap.of(BigDecimal.valueOf(140d), BigDecimal.valueOf(108d),
                BigDecimal.valueOf(20d), null, null);
        var corrected = AnionGap.of(BigDecimal.valueOf(140d), BigDecimal.valueOf(108d),
                BigDecimal.valueOf(20d), null, BigDecimal.valueOf(20d));

        assertThat(uncorrected.anionGapMmolPerL().doubleValue()).isCloseTo(12d, within(0.05d));
        assertThat(corrected.albuminCorrectedMmolPerL().doubleValue()).isCloseTo(17d, within(0.05d));
        assertThat(corrected.albuminCorrectionApplied()).isTrue();
    }

    @Test
    void anUncorrectedGapNeverClaimsToHaveBeenCorrected() {
        // Without an albumin there is no correction, and the flag must say so. A surface that reads
        // a null correction as "no correction needed" presents a hypoalbuminaemic patient's normal
        // gap as reassurance.
        var result = AnionGap.of(BigDecimal.valueOf(140d), BigDecimal.valueOf(100d),
                BigDecimal.valueOf(24d), null, null);

        assertThat(result.albuminCorrectedMmolPerL()).isNull();
        assertThat(result.albuminCorrectionApplied()).isFalse();
    }

    @Test
    void theDeltaRatioIsComputedOnlyWhenThereIsAnAcidosisToCompare() {
        // With a bicarbonate at or above reference the denominator is zero or negative and the ratio
        // is meaningless. Returning a number there — or a division-by-zero infinity — invites a
        // reader to diagnose a second acid-base disturbance that the arithmetic invented.
        var noAcidosis = AnionGap.of(BigDecimal.valueOf(140d), BigDecimal.valueOf(100d),
                BigDecimal.valueOf(24d), null, null);
        var acidosis = AnionGap.of(BigDecimal.valueOf(140d), BigDecimal.valueOf(100d),
                BigDecimal.valueOf(10d), null, null);

        assertThat(noAcidosis.deltaRatio()).isNull();
        // Gap 30, so ΔGap 18; bicarbonate 10, so ΔHCO₃ 14. 18/14 = 1.29.
        assertThat(acidosis.deltaRatio().doubleValue()).isCloseTo(1.29d, within(0.01d));
    }

    @Test
    void aMissingElectrolyteIsRefusedRatherThanTreatedAsZero() {
        // A chloride read as zero returns a gap around 116, which is not a number any reader will
        // trust — but a chloride read as a default reference value returns a plausible gap that is
        // simply wrong, and that is the one that gets acted on.
        assertThat(AnionGap.of(null, BigDecimal.valueOf(100d), BigDecimal.valueOf(24d), null, null)
                .reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(AnionGap.of(BigDecimal.valueOf(140d), null, BigDecimal.valueOf(24d), null, null)
                .reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(AnionGap.of(BigDecimal.valueOf(140d), BigDecimal.valueOf(100d), null, null, null)
                .reason()).isEqualTo(RefusalReason.INPUT_MISSING);
    }

    @Test
    void anImplausibleElectrolyteIsRefusedOnEveryOptionalFieldToo() {
        // The optional fields are optional, not unvalidated. An albumin of 4.2 entered in g/L would
        // add nine to the gap and manufacture a metabolic acidosis.
        assertThat(AnionGap.of(BigDecimal.valueOf(140d), BigDecimal.valueOf(100d),
                BigDecimal.valueOf(24d), null, BigDecimal.valueOf(4.2d)).reason())
                .isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(AnionGap.of(BigDecimal.valueOf(140d), BigDecimal.valueOf(100d),
                BigDecimal.valueOf(24d), BigDecimal.valueOf(40d), null).reason())
                .isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
    }
}
