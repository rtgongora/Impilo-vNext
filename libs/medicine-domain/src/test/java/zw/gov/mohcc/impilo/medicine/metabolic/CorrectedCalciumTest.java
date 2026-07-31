package zw.gov.mohcc.impilo.medicine.metabolic;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * A hypoalbuminaemic patient can show a normal total calcium while the corrected value is frankly
 * high. Each test names the harm the assertion stands between a patient and.
 */
class CorrectedCalciumTest {

    @Test
    void hypoalbuminaemiaUnmasksAHypercalcaemiaTheTotalCalciumHides() {
        // The case the correction exists for. Total calcium 2.35 mmol/L reads as reassuringly normal;
        // with an albumin of 20 g/L the corrected value is 2.75, which is hypercalcaemia and in a
        // patient with malignancy is an emergency. Acting on the uncorrected number is inaction.
        var result = CorrectedCalcium.of(BigDecimal.valueOf(2.35d), BigDecimal.valueOf(20d));

        assertThat(result.computed()).isTrue();
        assertThat(result.correctedCalciumMmolPerL().doubleValue()).isCloseTo(2.75d, within(0.01d));
        assertThat(result.contentVersion()).isEqualTo(CorrectedCalcium.CONTENT_VERSION);
    }

    @Test
    void aNormalAlbuminLeavesTheCalciumWhereItWas() {
        // At the reference albumin the correction term is zero. If it is not, the coefficient or the
        // reference has drifted and every other result in this class is wrong with it.
        var result = CorrectedCalcium.of(BigDecimal.valueOf(2.40d),
                CorrectedCalcium.REFERENCE_ALBUMIN_G_PER_L);

        assertThat(result.correctedCalciumMmolPerL()).isEqualByComparingTo(BigDecimal.valueOf(2.40d));
    }

    @Test
    void aHighAlbuminCorrectsDownwardsRatherThanOnlyEverUpwards() {
        // The correction is symmetrical. A one-directional implementation would report a
        // haemoconcentrated patient as hypercalcaemic and prompt treatment they do not need.
        var result = CorrectedCalcium.of(BigDecimal.valueOf(2.60d), BigDecimal.valueOf(50d));

        assertThat(result.correctedCalciumMmolPerL().doubleValue()).isLessThan(2.60d);
    }

    @Test
    void aMissingAlbuminIsRefusedAndTheUncorrectedValueIsNotPassedOffAsCorrected() {
        // The dangerous shortcut: return the measured calcium when albumin is absent. The number
        // then appears on screen under a "corrected calcium" label having been corrected by nothing.
        var result = CorrectedCalcium.of(BigDecimal.valueOf(2.35d), null);

        assertThat(result.computed()).isFalse();
        assertThat(result.correctedCalciumMmolPerL()).isNull();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("albumin");
        // The measured value is still carried, but only in the field that says it is the measured one.
        assertThat(result.totalCalciumMmolPerL()).isEqualByComparingTo(BigDecimal.valueOf(2.35d));
    }

    @Test
    void aCalciumInMgPerDlTypedIntoTheMmolFieldIsRefused() {
        // 9.5 mg/dL is an ordinary calcium and an impossible mmol/L one. Left to run it corrects to
        // roughly 9.9, a number no patient survives, which a reader is likelier to dismiss as a
        // glitch than to act on — so the refusal must be explicit.
        var result = CorrectedCalcium.of(BigDecimal.valueOf(9.5d), BigDecimal.valueOf(30d));

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(result.explanation()).contains("mg/dL");
    }

    @Test
    void anAlbuminInGramsPerDecilitreTypedIntoTheGramsPerLitreFieldIsRefused() {
        // 4.2 g/dL is a normal albumin; read as g/L it is near-total albumin loss and inflates the
        // corrected calcium by 0.7 mmol/L, turning a normal result into an apparent emergency.
        var result = CorrectedCalcium.of(BigDecimal.valueOf(2.35d), BigDecimal.valueOf(4.2d));

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(result.explanation()).contains("g/dL");
    }

    @Test
    void aMissingCalciumIsRefused() {
        var result = CorrectedCalcium.of(null, BigDecimal.valueOf(30d));

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
    }
}
