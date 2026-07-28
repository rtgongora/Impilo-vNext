package zw.gov.mohcc.impilo.medicine.frailty;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Clinical Frailty Scale is used at the front door of a hospital to decide how aggressively to
 * treat someone. A score that is off by one at either end changes that decision, and a score nobody
 * assigned must never appear at all.
 */
class ClinicalFrailtyScaleTest {

    @Test
    void everyScoreOneToNineMapsToItsPublishedLevel() {
        // The worked example is the scale itself: nine levels, nine numbers, in order. The names
        // are version 2.0 (2020), which renamed several levels — "Vulnerable" became "Living with
        // Very Mild Frailty" — so a stored level name is only interpretable with its version.
        assertThat(ClinicalFrailtyScale.fromScore(1).level()).isEqualTo(ClinicalFrailtyScale.VERY_FIT);
        assertThat(ClinicalFrailtyScale.fromScore(4).level())
                .isEqualTo(ClinicalFrailtyScale.LIVING_WITH_VERY_MILD_FRAILTY);
        assertThat(ClinicalFrailtyScale.fromScore(7).level())
                .isEqualTo(ClinicalFrailtyScale.LIVING_WITH_SEVERE_FRAILTY);
        assertThat(ClinicalFrailtyScale.fromScore(9).level())
                .isEqualTo(ClinicalFrailtyScale.TERMINALLY_ILL);

        for (int score = ClinicalFrailtyScale.MIN_SCORE; score <= ClinicalFrailtyScale.MAX_SCORE; score++) {
            var scoring = ClinicalFrailtyScale.fromScore(score);
            assertThat(scoring.computed()).isTrue();
            assertThat(scoring.level().score()).isEqualTo(score);
        }
    }

    @Test
    void theScaleHasExactlyNineLevelsInAscendingOrder() {
        // A tenth level, or a gap, would silently change what every stored score means. Asserted
        // structurally so that adding a constant to the enum fails here rather than in a ward.
        assertThat(ClinicalFrailtyScale.values()).hasSize(9);
        assertThat(Arrays.stream(ClinicalFrailtyScale.values()).map(ClinicalFrailtyScale::score))
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Test
    void aScoreOffTheScaleIsRefusedRatherThanClamped() {
        // Clamping is the tempting fix and the dangerous one. A 0 clamped to 1 records a very fit
        // patient; a 12 clamped to 9 records a terminally ill one. Both are plausible-looking
        // results nobody assessed, and both are stored permanently.
        var zero = ClinicalFrailtyScale.fromScore(0);
        var twelve = ClinicalFrailtyScale.fromScore(12);

        assertThat(zero.computed()).isFalse();
        assertThat(zero.level()).isNull();
        assertThat(zero.reason()).isEqualTo(RefusalReason.OUTSIDE_INSTRUMENT_RANGE);
        assertThat(zero.explanation()).contains("clamped");
        assertThat(twelve.reason()).isEqualTo(RefusalReason.OUTSIDE_INSTRUMENT_RANGE);
    }

    @Test
    void anUnrecordedScoreIsNotAFitPatient() {
        // The scale is a clinician's global judgement and cannot be derived from anything else in
        // the record, so an absent score has to stay absent.
        var result = ClinicalFrailtyScale.fromScore(null);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("clinician");
    }

    @Test
    void theFrailtyLevelsAreFiveThroughEight() {
        // The scale's own naming reserves the word "frailty" for 5 to 8. Level 9 is terminal
        // illness in someone who may not be frail at all, and treating it as the top of a frailty
        // gradient misreads a dying patient as a maximally frail one.
        assertThat(ClinicalFrailtyScale.LIVING_WITH_MILD_FRAILTY.livingWithFrailty()).isTrue();
        assertThat(ClinicalFrailtyScale.LIVING_WITH_VERY_SEVERE_FRAILTY.livingWithFrailty()).isTrue();
        assertThat(ClinicalFrailtyScale.LIVING_WITH_VERY_MILD_FRAILTY.livingWithFrailty()).isFalse();
        assertThat(ClinicalFrailtyScale.TERMINALLY_ILL.livingWithFrailty()).isFalse();
    }

    @Test
    void aPatientUnderSixtyFiveGetsAnApplicabilityCaveatRatherThanARefusal() {
        // The scale was validated from 65, but a geriatrician scoring a 58-year-old with multiple
        // long-term conditions may have a good reason. This library does not overrule a clinician;
        // it makes sure the caveat travels with the score to whoever reads it next.
        assertThat(ClinicalFrailtyScale.applicabilityCaveat(58)).contains("65");
        assertThat(ClinicalFrailtyScale.applicabilityCaveat(58)).contains("single-system disability");
        assertThat(ClinicalFrailtyScale.applicabilityCaveat(70)).isNull();
        assertThat(ClinicalFrailtyScale.applicabilityCaveat(null)).contains("not recorded");
    }

    @Test
    void theOfficialDescriptorsAreNotShippedAndTheFlagSaysSo() {
        // The CFS descriptor text is copyright Dalhousie University and Impilo holds no licence.
        // The summaries here are Impilo paraphrases, adequate to tell the levels apart in a picker
        // and not adequate to score a patient against. A deployment that shows the scale to
        // clinicians needs the licensed chart, and this flag is what a build guard should read.
        assertThat(ClinicalFrailtyScale.OFFICIAL_DESCRIPTORS_LICENSED).isFalse();
        for (ClinicalFrailtyScale level : ClinicalFrailtyScale.values()) {
            assertThat(level.summary()).isNotBlank();
            assertThat(level.label()).isNotBlank();
        }
    }
}
