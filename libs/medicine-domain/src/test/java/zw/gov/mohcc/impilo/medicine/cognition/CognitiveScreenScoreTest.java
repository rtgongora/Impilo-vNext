package zw.gov.mohcc.impilo.medicine.cognition;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The clinical point of this suite is mostly negative: it asserts what this class refuses to know.
 * Cognitive screen cut-offs move with education and language, and Zimbabwe's widely used screens
 * are validated against local education profiles. A cut-off compiled into a library cannot be
 * changed by a ministry without a release, so none is compiled in.
 */
class CognitiveScreenScoreTest {

    @Test
    void aScoreIsExpressedAsAProportionOfTheInstrumentMaximum() {
        // The worked example: 24 out of 30 is 80.0%, which is the 75-to-under-90 band. The number
        // 24/30 is famously the MMSE cut-off; note that this class neither knows nor says so.
        var result = CognitiveScreenScore.of(24, 30);

        assertThat(result.computed()).isTrue();
        assertThat(result.percentOfMaximum().doubleValue()).isEqualTo(80.0d);
        assertThat(result.band()).isEqualTo(CognitiveScreenScore.ProportionBand.FROM_75_TO_UNDER_90_PERCENT);
        assertThat(result.contentVersion()).isEqualTo(CognitiveScreenScore.CONTENT_VERSION);
    }

    @Test
    void bandBoundariesAreArithmeticFifthsAndNothingElse() {
        // Each boundary is a round proportion, not a validated cut-off. If one of these ever lands
        // on 26/30 or 24/30, somebody has smuggled a clinical decision into the library.
        assertThat(CognitiveScreenScore.of(100, 100).band())
                .isEqualTo(CognitiveScreenScore.ProportionBand.AT_LEAST_90_PERCENT);
        assertThat(CognitiveScreenScore.of(90, 100).band())
                .isEqualTo(CognitiveScreenScore.ProportionBand.AT_LEAST_90_PERCENT);
        assertThat(CognitiveScreenScore.of(89, 100).band())
                .isEqualTo(CognitiveScreenScore.ProportionBand.FROM_75_TO_UNDER_90_PERCENT);
        assertThat(CognitiveScreenScore.of(75, 100).band())
                .isEqualTo(CognitiveScreenScore.ProportionBand.FROM_75_TO_UNDER_90_PERCENT);
        assertThat(CognitiveScreenScore.of(74, 100).band())
                .isEqualTo(CognitiveScreenScore.ProportionBand.FROM_50_TO_UNDER_75_PERCENT);
        assertThat(CognitiveScreenScore.of(50, 100).band())
                .isEqualTo(CognitiveScreenScore.ProportionBand.FROM_50_TO_UNDER_75_PERCENT);
        assertThat(CognitiveScreenScore.of(49, 100).band())
                .isEqualTo(CognitiveScreenScore.ProportionBand.FROM_25_TO_UNDER_50_PERCENT);
        assertThat(CognitiveScreenScore.of(25, 100).band())
                .isEqualTo(CognitiveScreenScore.ProportionBand.FROM_25_TO_UNDER_50_PERCENT);
        assertThat(CognitiveScreenScore.of(24, 100).band())
                .isEqualTo(CognitiveScreenScore.ProportionBand.BELOW_25_PERCENT);
        assertThat(CognitiveScreenScore.of(0, 30).band())
                .isEqualTo(CognitiveScreenScore.ProportionBand.BELOW_25_PERCENT);
    }

    @Test
    void noBandNameSuggestsImpairment() {
        // The band names are the surface a UI will render, and a name like MILD_IMPAIRMENT would be
        // a diagnosis derived from a fraction. Every label states a proportion instead, so it
        // cannot be misread when it appears beside a patient's name.
        for (var band : CognitiveScreenScore.ProportionBand.values()) {
            assertThat(band.label()).contains("maximum");
        }
        assertThat(Arrays.stream(CognitiveScreenScore.ProportionBand.values())
                .map(Enum::name))
                .noneMatch(name -> name.contains("IMPAIR") || name.contains("DEMENTIA")
                        || name.contains("NORMAL") || name.contains("ABNORMAL"));
    }

    @Test
    void aMissingTotalIsNotAZeroTotal() {
        // Zero is the worst score the instrument can produce. Reading an unrecorded screen as zero
        // would put every patient nobody screened at the bottom of the list.
        var result = CognitiveScreenScore.of(null, 30);

        assertThat(result.computed()).isFalse();
        assertThat(result.band()).isNull();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("zero");
    }

    @Test
    void aMissingMaximumIsRefusedBecauseAProportionNeedsOne() {
        var result = CognitiveScreenScore.of(24, null);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
    }

    @Test
    void aScoreAboveTheMaximumIsRefusedRatherThanCapped() {
        // The usual cause is the maximum for a different instrument: a MoCA total of 28 recorded
        // against a 20-point screen. Capping it to 20 produces full marks from an impossible input,
        // and every band computed from that maximum is wrong too.
        var result = CognitiveScreenScore.of(28, 20);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUTS_INCONSISTENT);
        assertThat(result.explanation()).contains("different instrument");
    }

    @Test
    void aNegativeScoreOrANonPositiveMaximumIsRefused() {
        assertThat(CognitiveScreenScore.of(-1, 30).reason())
                .isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(CognitiveScreenScore.of(10, 0).reason())
                .isEqualTo(RefusalReason.INPUTS_INCONSISTENT);
        assertThat(CognitiveScreenScore.of(10, -30).reason())
                .isEqualTo(RefusalReason.INPUTS_INCONSISTENT);
    }

    @Test
    void anImplausibleMaximumIsRefused() {
        // A maximum of 3000 is a field mix-up, not an instrument; the longest screens in use top
        // out far below the ceiling.
        var result = CognitiveScreenScore.of(10, 3000);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
    }

    @Test
    void theCutOffComparisonIsTheCallersToSupply() {
        // The comparison is arithmetic; the number 24 is not. Passing it in makes governed content
        // — which carries the instrument, its version and its education adjustment — the owner of
        // the clinical decision. A null cut-off yields null, never false, so an absent comparison
        // cannot be read as "did not screen positive".
        var result = CognitiveScreenScore.of(23, 30);

        assertThat(result.atOrBelowCutOff(24)).isTrue();
        assertThat(result.atOrBelowCutOff(20)).isFalse();
        assertThat(result.atOrBelowCutOff(null)).isNull();
        assertThat(CognitiveScreenScore.of(null, 30).atOrBelowCutOff(24)).isNull();
    }

    @Test
    void changeIsOnlyComputedBetweenTwoScoresOnTheSameInstrument() {
        // A drop from 26/30 to 20/26 is not a six-point decline, it is two different tests. Serial
        // cognitive scores are how decline is detected, so comparing across instruments would
        // manufacture deterioration in someone who was simply screened with a different tool.
        var earlier = CognitiveScreenScore.of(26, 30);
        var later = CognitiveScreenScore.of(20, 30);
        var differentInstrument = CognitiveScreenScore.of(20, 26);

        assertThat(later.changeFrom(earlier)).isEqualTo(-6);
        assertThat(differentInstrument.changeFrom(earlier)).isNull();
        assertThat(later.changeFrom(null)).isNull();
        assertThat(later.changeFrom(CognitiveScreenScore.of(null, 30))).isNull();
    }
}
