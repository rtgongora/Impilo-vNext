package zw.gov.mohcc.impilo.medicine.cardio;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NYHA class is the number that decides whether someone with heart failure is on a follow-up list,
 * and it is derived entirely from three questions. The tests below are about what happens when one
 * of those questions was not asked, because that is the case a form fills in for you.
 */
class NyhaClassTest {

    @Test
    void symptomsAtRestAreClassFour() {
        // The worked example for the top of the ladder. Breathlessness sitting still is the
        // definition of class IV; nothing below it needs evaluating.
        var result = NyhaClass.classify(true, true, true);

        assertThat(result.computed()).isTrue();
        assertThat(result.nyhaClass()).isEqualTo(NyhaClass.CLASS_IV);
        assertThat(result.nyhaClass().romanNumeral()).isEqualTo("IV");
        assertThat(result.contentVersion()).isEqualTo(NyhaClass.CONTENT_VERSION);
    }

    @Test
    void symptomsOnLessThanOrdinaryActivityAreClassThree() {
        // Comfortable at rest but breathless walking to the gate. Class III is the boundary at which
        // most heart-failure guidelines change what is offered, so III-versus-II is the distinction
        // this classification most has to get right.
        var result = NyhaClass.classify(false, true, true);

        assertThat(result.nyhaClass()).isEqualTo(NyhaClass.CLASS_III);
    }

    @Test
    void symptomsOnOrdinaryActivityOnlyAreClassTwo() {
        var result = NyhaClass.classify(false, false, true);

        assertThat(result.nyhaClass()).isEqualTo(NyhaClass.CLASS_II);
    }

    @Test
    void noSymptomsAtAnyExertionIsClassOne() {
        // All three explicitly denied. Class I is only reachable from three recorded negatives —
        // never from silence, which is the next test.
        var result = NyhaClass.classify(false, false, false);

        assertThat(result.nyhaClass()).isEqualTo(NyhaClass.CLASS_I);
    }

    @Test
    void nothingRecordedIsNotClassOne() {
        // The failure this class exists to prevent. A blank assessment form scored as class I says
        // the patient has no limitation, which is the answer that takes them off the review list.
        var result = NyhaClass.classify(null, null, null);

        assertThat(result.computed()).isFalse();
        assertThat(result.nyhaClass()).isNull();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
    }

    @Test
    void anUnaskedRestQuestionStopsTheLadderAtTheTop() {
        // Class IV cannot be excluded if nobody asked about rest, so no lower class may be assigned
        // even though the two exertion answers on their own would suggest class II. Starting the
        // walk one rung down is how a class IV patient becomes a class II patient.
        var result = NyhaClass.classify(null, false, true);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("class IV");
    }

    @Test
    void anUnaskedLessThanOrdinaryQuestionStopsTheLadderAtClassThree() {
        var result = NyhaClass.classify(false, null, true);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("class");
    }

    @Test
    void anUnaskedOrdinaryActivityQuestionCannotYieldClassOne() {
        // Two negatives and a silence is not three negatives. Class I is a positive finding of no
        // limitation and must not be reached by running out of questions.
        var result = NyhaClass.classify(false, false, null);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
    }

    @Test
    void restSymptomsDeniedOnOrdinaryActivityAreInconsistentRatherThanResolved() {
        // Someone breathless sitting still is breathless walking. These two answers are two classes
        // apart, there is no basis for preferring either, and picking one records a coin toss as a
        // clinical finding. The clinician has to go back and re-ask.
        var result = NyhaClass.classify(true, true, false);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUTS_INCONSISTENT);
        assertThat(result.explanation()).contains("rest");
    }

    @Test
    void lessThanOrdinarySymptomsDeniedOnOrdinaryActivityAreInconsistent() {
        // Ordinary activity is the greater exertion, so it cannot be the better tolerated one.
        // Usually a mis-ticked form; always worth a second look before a class is stored.
        var result = NyhaClass.classify(false, true, false);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUTS_INCONSISTENT);
    }

    @Test
    void restSymptomsDeniedOnLessThanOrdinaryActivityAreInconsistent() {
        var result = NyhaClass.classify(true, false, true);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUTS_INCONSISTENT);
    }

    @Test
    void everyClassCarriesItsPublishedCriteriaText() {
        // The class number on its own means nothing to anyone who has not memorised the scale, and
        // the whole instrument is a description of function. The text has to travel with the number.
        for (NyhaClass value : NyhaClass.values()) {
            assertThat(value.criteria()).isNotBlank();
            assertThat(value.romanNumeral()).isNotBlank();
        }
        assertThat(NyhaClass.CLASS_II.criteria()).contains("ordinary physical activity");
        assertThat(NyhaClass.CLASS_III.criteria()).contains("less than ordinary");
    }

    @Test
    void numeralsMapBothWaysAndAnOffScaleNumeralIsNotAClass() {
        assertThat(NyhaClass.fromNumeral(1)).isEqualTo(NyhaClass.CLASS_I);
        assertThat(NyhaClass.fromNumeral(4)).isEqualTo(NyhaClass.CLASS_IV);
        assertThat(NyhaClass.fromNumeral(0)).isNull();
        assertThat(NyhaClass.fromNumeral(5)).isNull();
        assertThat(NyhaClass.fromNumeral(null)).isNull();
    }
}
