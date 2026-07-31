package zw.gov.mohcc.impilo.medicine.respiratory;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CURB-65 informs whether a pneumonia is managed at home or in hospital. Each test names the harm
 * the assertion stands between a patient and.
 */
class Curb65ScoreTest {

    @Test
    void aWellYoungPatientScoresZeroAndBandsLow() {
        var result = Curb65Score.of(false, BigDecimal.valueOf(4d), 18, BigDecimal.valueOf(120d),
                BigDecimal.valueOf(80d), 30);

        assertThat(result.computed()).isTrue();
        assertThat(result.score()).isZero();
        assertThat(result.band()).isEqualTo(Curb65Score.RiskBand.LOW);
        assertThat(result.instrument()).isEqualTo(Curb65Score.Instrument.CURB_65);
        assertThat(result.contentVersion()).isEqualTo(Curb65Score.CONTENT_VERSION);
    }

    @Test
    void aSeverePneumoniaScoresFiveAndBandsHigh() {
        var result = Curb65Score.of(true, BigDecimal.valueOf(12d), 34, BigDecimal.valueOf(85d),
                BigDecimal.valueOf(50d), 78);

        assertThat(result.score()).isEqualTo(5);
        assertThat(result.band()).isEqualTo(Curb65Score.RiskBand.HIGH);
        assertThat(result.confusionPoint()).isTrue();
        assertThat(result.ureaPoint()).isTrue();
        assertThat(result.respiratoryPoint()).isTrue();
        assertThat(result.pressurePoint()).isTrue();
        assertThat(result.agePoint()).isTrue();
    }

    @Test
    void aMissingUreaIsRefusedRatherThanScoredAsAFourItemCurb65() {
        // The routine shortcut at a facility without chemistry: score the other four and call the
        // result CURB-65. That reports a maximum of 4 as though it were out of 5, so a patient who
        // would have scored 3 is presented as 2 and sent home.
        var result = Curb65Score.of(true, null, 34, BigDecimal.valueOf(85d),
                BigDecimal.valueOf(50d), 78);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("CRB-65");
    }

    @Test
    void crb65IsAvailableAsANamedInstrumentWithItsOwnBands() {
        // The honest alternative when there is no laboratory: a different score, labelled as such.
        var result = Curb65Score.crb65(true, 34, BigDecimal.valueOf(85d), BigDecimal.valueOf(50d), 78);

        assertThat(result.computed()).isTrue();
        assertThat(result.score()).isEqualTo(4);
        assertThat(result.instrument()).isEqualTo(Curb65Score.Instrument.CRB_65);
        assertThat(result.instrument().label()).isEqualTo("CRB-65");
        assertThat(result.instrument().maximumScore()).isEqualTo(4);
    }

    @Test
    void theUreaItemIsAbsentRatherThanZeroOnCrb65() {
        // Null and false are different claims. A false would say the urea was measured and normal,
        // which is a statement about a test nobody ran.
        var result = Curb65Score.crb65(false, 18, BigDecimal.valueOf(120d), BigDecimal.valueOf(80d), 30);

        assertThat(result.ureaPoint()).isNull();
        assertThat(result.confusionPoint()).isFalse();
    }

    @Test
    void theTwoInstrumentsBandTheSameCountDifferently() {
        // A score of 1 is LOW on CURB-65 and INTERMEDIATE on CRB-65. Showing a CRB-65 total against
        // CURB-65 bands is how a score gets misread as more reassuring than it is.
        var curb = Curb65Score.of(false, BigDecimal.valueOf(4d), 18, BigDecimal.valueOf(120d),
                BigDecimal.valueOf(80d), 70);
        var crb = Curb65Score.crb65(false, 18, BigDecimal.valueOf(120d), BigDecimal.valueOf(80d), 70);

        assertThat(curb.score()).isEqualTo(1);
        assertThat(crb.score()).isEqualTo(1);
        assertThat(curb.band()).isEqualTo(Curb65Score.RiskBand.LOW);
        assertThat(crb.band()).isEqualTo(Curb65Score.RiskBand.INTERMEDIATE);
    }

    @Test
    void everyCutPointFallsOnThePublishedBoundary() {
        // Urea scores above 7, not at it; respiratory rate at 30, not above; systolic below 90;
        // diastolic at 60; age at 65. Each is one point, and one point crosses a band.
        assertThat(Curb65Score.of(false, BigDecimal.valueOf(7d), 18, BigDecimal.valueOf(120d),
                BigDecimal.valueOf(80d), 30).ureaPoint()).isFalse();
        assertThat(Curb65Score.of(false, BigDecimal.valueOf(7.1d), 18, BigDecimal.valueOf(120d),
                BigDecimal.valueOf(80d), 30).ureaPoint()).isTrue();

        assertThat(Curb65Score.of(false, BigDecimal.valueOf(4d), 29, BigDecimal.valueOf(120d),
                BigDecimal.valueOf(80d), 30).respiratoryPoint()).isFalse();
        assertThat(Curb65Score.of(false, BigDecimal.valueOf(4d), 30, BigDecimal.valueOf(120d),
                BigDecimal.valueOf(80d), 30).respiratoryPoint()).isTrue();

        assertThat(Curb65Score.of(false, BigDecimal.valueOf(4d), 18, BigDecimal.valueOf(90d),
                BigDecimal.valueOf(70d), 30).pressurePoint()).isFalse();
        assertThat(Curb65Score.of(false, BigDecimal.valueOf(4d), 18, BigDecimal.valueOf(89d),
                BigDecimal.valueOf(70d), 30).pressurePoint()).isTrue();
        assertThat(Curb65Score.of(false, BigDecimal.valueOf(4d), 18, BigDecimal.valueOf(120d),
                BigDecimal.valueOf(60d), 30).pressurePoint()).isTrue();

        assertThat(Curb65Score.of(false, BigDecimal.valueOf(4d), 18, BigDecimal.valueOf(120d),
                BigDecimal.valueOf(80d), 64).agePoint()).isFalse();
        assertThat(Curb65Score.of(false, BigDecimal.valueOf(4d), 18, BigDecimal.valueOf(120d),
                BigDecimal.valueOf(80d), 65).agePoint()).isTrue();
    }

    @Test
    void aTransposedBloodPressureIsRefusedRatherThanQuietlyReordered() {
        // Entering 60/90 instead of 90/60 is a common slip. Silently swapping them hides a
        // data-quality problem the clinician should see; scoring them as entered invents a point.
        var result = Curb65Score.of(false, BigDecimal.valueOf(4d), 18, BigDecimal.valueOf(60d),
                BigDecimal.valueOf(90d), 30);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUTS_INCONSISTENT);
    }

    @Test
    void anUnassessedConfusionIsRefusedRatherThanReadAsAbsent() {
        var result = Curb65Score.of(null, BigDecimal.valueOf(4d), 18, BigDecimal.valueOf(120d),
                BigDecimal.valueOf(80d), 30);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
    }

    @Test
    void anImplausibleObservationIsRefused() {
        assertThat(Curb65Score.of(false, BigDecimal.valueOf(4d), 250, BigDecimal.valueOf(120d),
                BigDecimal.valueOf(80d), 30).reason())
                .isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(Curb65Score.of(false, BigDecimal.valueOf(4d), 18, BigDecimal.valueOf(120d),
                BigDecimal.valueOf(80d), 199).reason())
                .isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
    }
}
