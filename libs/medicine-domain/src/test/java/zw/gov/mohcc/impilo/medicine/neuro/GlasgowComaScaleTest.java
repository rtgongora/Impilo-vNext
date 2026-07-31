package zw.gov.mohcc.impilo.medicine.neuro;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The GCS drives escalation, imaging and airway decisions. Each test names the harm the assertion
 * stands between a patient and.
 */
class GlasgowComaScaleTest {

    @Test
    void afullyAlertPatientScoresFifteen() {
        var result = GlasgowComaScale.of(GlasgowComaScale.EyeResponse.SPONTANEOUS,
                GlasgowComaScale.VerbalResponse.ORIENTED,
                GlasgowComaScale.MotorResponse.OBEYS_COMMANDS);

        assertThat(result.computed()).isTrue();
        assertThat(result.total()).isEqualTo(GlasgowComaScale.MAX_SCORE);
        assertThat(result.severity()).isEqualTo(GlasgowComaScale.Severity.MILD);
        assertThat(result.notation()).isEqualTo("E4 V5 M6");
        assertThat(result.contentVersion()).isEqualTo(GlasgowComaScale.CONTENT_VERSION);
    }

    @Test
    void anUnresponsivePatientScoresThreeRatherThanZero() {
        // The scale has no zero. An implementation that sums absent responses to 0 produces a number
        // outside the instrument, and any band derived from it is meaningless.
        var result = GlasgowComaScale.of(GlasgowComaScale.EyeResponse.NONE,
                GlasgowComaScale.VerbalResponse.NONE,
                GlasgowComaScale.MotorResponse.NONE);

        assertThat(result.total()).isEqualTo(GlasgowComaScale.MIN_SCORE);
        assertThat(result.severity()).isEqualTo(GlasgowComaScale.Severity.SEVERE);
    }

    @Test
    void anIntubatedPatientYieldsNoTotalRatherThanBeingScoredAsVerballyUnresponsive() {
        // The single most consequential rule in the scale. An alert, obeying, intubated patient
        // scored V1 totals 11 — moderate head injury — and that number drives imaging, escalation
        // and prognostic conversations for a patient who is fully conscious.
        var result = GlasgowComaScale.of(GlasgowComaScale.EyeResponse.SPONTANEOUS,
                GlasgowComaScale.VerbalResponse.NOT_TESTABLE,
                GlasgowComaScale.MotorResponse.OBEYS_COMMANDS);

        assertThat(result.computed()).isFalse();
        assertThat(result.total()).isNull();
        assertThat(result.severity()).isNull();
        assertThat(result.hasUntestableComponent()).isTrue();
        assertThat(result.reason()).isEqualTo(RefusalReason.INSTRUMENT_NOT_APPLICABLE);
        assertThat(result.notation()).isEqualTo("E4 VNT M6");
    }

    @Test
    void anUntestableComponentStillReportsTheComponentsThatWereAssessed() {
        // Refusing the total is not refusing the assessment. E4 and M6 remain valid observations and
        // are what should be recorded; discarding them would lose real clinical information.
        var result = GlasgowComaScale.of(GlasgowComaScale.EyeResponse.SPONTANEOUS,
                GlasgowComaScale.VerbalResponse.NOT_TESTABLE,
                GlasgowComaScale.MotorResponse.OBEYS_COMMANDS);

        assertThat(result.eyePoints()).isEqualTo(4);
        assertThat(result.motorPoints()).isEqualTo(6);
        assertThat(result.verbalPoints()).isNull();
    }

    @Test
    void everyComponentCanBeUntestableAndNoneSubstitutesOne() {
        // Paralysis and periorbital swelling make the motor and eye components untestable for the
        // same reason intubation makes the verbal one untestable.
        var eyeSwollen = GlasgowComaScale.of(GlasgowComaScale.EyeResponse.NOT_TESTABLE,
                GlasgowComaScale.VerbalResponse.ORIENTED,
                GlasgowComaScale.MotorResponse.OBEYS_COMMANDS);
        var paralysed = GlasgowComaScale.of(GlasgowComaScale.EyeResponse.SPONTANEOUS,
                GlasgowComaScale.VerbalResponse.ORIENTED,
                GlasgowComaScale.MotorResponse.NOT_TESTABLE);

        assertThat(eyeSwollen.total()).isNull();
        assertThat(eyeSwollen.notation()).isEqualTo("ENT V5 M6");
        assertThat(paralysed.total()).isNull();
        assertThat(paralysed.notation()).isEqualTo("E4 V5 MNT");
    }

    @Test
    void theSeverityBandsFallOnTheConventionalBoundaries() {
        assertThat(severityAt(GlasgowComaScale.EyeResponse.SPONTANEOUS,
                GlasgowComaScale.VerbalResponse.WORDS,
                GlasgowComaScale.MotorResponse.NORMAL_FLEXION))
                .isEqualTo(GlasgowComaScale.Severity.MODERATE); // 4+3+4 = 11

        assertThat(severityAt(GlasgowComaScale.EyeResponse.SPONTANEOUS,
                GlasgowComaScale.VerbalResponse.CONFUSED,
                GlasgowComaScale.MotorResponse.LOCALISING))
                .isEqualTo(GlasgowComaScale.Severity.MILD); // 4+4+5 = 13

        assertThat(severityAt(GlasgowComaScale.EyeResponse.TO_PRESSURE,
                GlasgowComaScale.VerbalResponse.SOUNDS,
                GlasgowComaScale.MotorResponse.NORMAL_FLEXION))
                .isEqualTo(GlasgowComaScale.Severity.SEVERE); // 2+2+4 = 8
    }

    private static GlasgowComaScale.Severity severityAt(GlasgowComaScale.EyeResponse eye,
                                                        GlasgowComaScale.VerbalResponse verbal,
                                                        GlasgowComaScale.MotorResponse motor) {
        return GlasgowComaScale.of(eye, verbal, motor).severity();
    }

    @Test
    void anUnrecordedComponentIsRefusedAndIsNotTheSameAsAnUntestableOne() {
        // Null means nobody assessed it. NOT_TESTABLE means it was assessed and cannot be tested.
        // The first is a documentation gap; the second is a clinical finding.
        var result = GlasgowComaScale.of(GlasgowComaScale.EyeResponse.SPONTANEOUS, null,
                GlasgowComaScale.MotorResponse.OBEYS_COMMANDS);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.hasUntestableComponent()).isFalse();
        assertThat(result.notation()).isNull();
    }

    @Test
    void componentsAreCarriedSoTheTotalCanBeCheckedAgainstThem() {
        // E2V2M5 and E4V1M4 both total 9 and mean different things. A stored total without its
        // components cannot be re-read.
        var result = GlasgowComaScale.of(GlasgowComaScale.EyeResponse.TO_PRESSURE,
                GlasgowComaScale.VerbalResponse.SOUNDS,
                GlasgowComaScale.MotorResponse.LOCALISING);

        assertThat(result.total()).isEqualTo(9);
        assertThat(result.eyePoints()).isEqualTo(2);
        assertThat(result.verbalPoints()).isEqualTo(2);
        assertThat(result.motorPoints()).isEqualTo(5);
        assertThat(result.notation()).isEqualTo("E2 V2 M5");
    }
}
