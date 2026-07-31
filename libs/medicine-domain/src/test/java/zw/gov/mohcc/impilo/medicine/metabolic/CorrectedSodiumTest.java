package zw.gov.mohcc.impilo.medicine.metabolic;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Correcting sodium for hyperglycaemia changes how alarming a hyponatraemia looks, and therefore
 * whether hypertonic saline is reached for. Each test names the harm the assertion stands between a
 * patient and.
 */
class CorrectedSodiumTest {

    @Test
    void theKetoacidoticPatientsApparentHyponatraemiaIsLargelyDilutional() {
        // Sodium 130 with a glucose of 30 mmol/L. Katz: 130 + 1.6 × (30 − 5.5)/5.5 = 137.1.
        // Untreated, the measured 130 invites sodium-directed treatment when the glucose is the
        // problem — and correcting sodium before glucose in DKA is how cerebral oedema happens.
        var result = CorrectedSodium.of(BigDecimal.valueOf(130d), BigDecimal.valueOf(30d));

        assertThat(result.computed()).isTrue();
        assertThat(result.katzMmolPerL().doubleValue()).isCloseTo(137.1d, within(0.1d));
        assertThat(result.contentVersion()).isEqualTo(CorrectedSodium.CONTENT_VERSION);
    }

    @Test
    void bothFactorsAreReturnedAndTheyDisagreeBySeveralMillimolesAtHighGlucose() {
        // Katz 1.6 and Hillier 2.4 diverge as glucose rises, and at DKA concentrations the gap
        // straddles the threshold at which a sodium looks reassuring rather than alarming. Showing
        // one number hides that disagreement.
        var result = CorrectedSodium.of(BigDecimal.valueOf(130d), BigDecimal.valueOf(40d));

        assertThat(result.hillierMmolPerL().doubleValue())
                .isGreaterThan(result.katzMmolPerL().doubleValue());
        assertThat(result.hillierMmolPerL().subtract(result.katzMmolPerL()).doubleValue())
                .isGreaterThan(4d);
    }

    @Test
    void aNormalGlucoseIsRefusedRatherThanCorrectingSodiumInTheWrongDirection() {
        // Below the threshold the formula subtracts, quietly raising a sodium that needed no
        // correction — or worse, making a real hyponatraemia look milder than it is. That patient
        // has a genuine hyponatraemia that needs its own explanation, not an adjustment.
        var result = CorrectedSodium.of(BigDecimal.valueOf(125d), BigDecimal.valueOf(4.5d));

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INSTRUMENT_NOT_APPLICABLE);
        assertThat(result.explanation()).contains("real hyponatraemia");
        assertThat(result.measuredSodiumMmolPerL()).isEqualByComparingTo(BigDecimal.valueOf(125d));
    }

    @Test
    void aMissingGlucoseIsRefusedAndTheMeasuredSodiumIsNotPassedOffAsCorrected() {
        var result = CorrectedSodium.of(BigDecimal.valueOf(130d), null);

        assertThat(result.computed()).isFalse();
        assertThat(result.katzMmolPerL()).isNull();
        assertThat(result.hillierMmolPerL()).isNull();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("glucose");
    }

    @Test
    void aGlucoseInMgPerDecilitreTypedIntoTheMmolFieldIsRefused() {
        // 250 mg/dL is a routine hyperglycaemia; read as mmol/L it is roughly eighteen times too
        // high and inflates the corrected sodium by more than seventy millimoles.
        var result = CorrectedSodium.of(BigDecimal.valueOf(130d), BigDecimal.valueOf(250d));

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(result.explanation()).contains("mg/dL");
    }

    @Test
    void anImplausibleSodiumIsRefused() {
        assertThat(CorrectedSodium.of(BigDecimal.valueOf(14d), BigDecimal.valueOf(20d)).reason())
                .isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
    }

    @Test
    void aMissingSodiumIsRefused() {
        assertThat(CorrectedSodium.of(null, BigDecimal.valueOf(20d)).reason())
                .isEqualTo(RefusalReason.INPUT_MISSING);
    }
}
