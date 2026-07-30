package zw.gov.mohcc.impilo.medicine.anthropometry;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BodyMassIndexTest {

    private static BodyMassIndex.Bmi adult(String heightCm, String weightKg) {
        return BodyMassIndex.of(new BigDecimal(heightCm), new BigDecimal(weightKg), 40);
    }

    @Test
    void theSeventyKiloOneSeventyAdultIsTwentyFourPointTwoAndInTheNormalRange() {
        BodyMassIndex.Bmi result = adult("170", "70");

        assertThat(result.computed()).isTrue();
        assertThat(result.bmi()).isEqualByComparingTo("24.2");
        assertThat(result.category()).isEqualTo(BodyMassIndex.Category.NORMAL);
    }

    @Test
    void everyCutPointIsInclusiveAtItsLowerBoundAsPublished() {
        assertThat(BodyMassIndex.classify(new BigDecimal("18.49")))
                .isEqualTo(BodyMassIndex.Category.UNDERWEIGHT);
        assertThat(BodyMassIndex.classify(new BigDecimal("18.50")))
                .isEqualTo(BodyMassIndex.Category.NORMAL);
        assertThat(BodyMassIndex.classify(new BigDecimal("24.99")))
                .isEqualTo(BodyMassIndex.Category.NORMAL);
        assertThat(BodyMassIndex.classify(new BigDecimal("25.00")))
                .isEqualTo(BodyMassIndex.Category.OVERWEIGHT);
        assertThat(BodyMassIndex.classify(new BigDecimal("29.99")))
                .isEqualTo(BodyMassIndex.Category.OVERWEIGHT);
        assertThat(BodyMassIndex.classify(new BigDecimal("30.00")))
                .isEqualTo(BodyMassIndex.Category.OBESE_CLASS_I);
        assertThat(BodyMassIndex.classify(new BigDecimal("34.99")))
                .isEqualTo(BodyMassIndex.Category.OBESE_CLASS_I);
        assertThat(BodyMassIndex.classify(new BigDecimal("35.00")))
                .isEqualTo(BodyMassIndex.Category.OBESE_CLASS_II);
        assertThat(BodyMassIndex.classify(new BigDecimal("39.99")))
                .isEqualTo(BodyMassIndex.Category.OBESE_CLASS_II);
        assertThat(BodyMassIndex.classify(new BigDecimal("40.00")))
                .isEqualTo(BodyMassIndex.Category.OBESE_CLASS_III);
    }

    @Test
    void aChildKeepsTheArithmeticButIsNotBandedAgainstAdultCutPoints() {
        // A 7-year-old at 16.0 kg/m² is entirely normal for age. The adult table would call this
        // underweight, which is the whole reason the band is withheld.
        BodyMassIndex.Bmi result = BodyMassIndex.of(new BigDecimal("125"), new BigDecimal("25"), 7);

        assertThat(result.computed()).isFalse();
        assertThat(result.category()).isNull();
        assertThat(result.reason()).isEqualTo(RefusalReason.INSTRUMENT_NOT_APPLICABLE);
        assertThat(result.hasIndex()).isTrue();
        assertThat(result.bmi()).isEqualByComparingTo("16.0");
        assertThat(result.explanation()).contains("growth references");
    }

    @Test
    void theAdultBoundaryItselfIsBanded() {
        BodyMassIndex.Bmi justAdult = BodyMassIndex.of(new BigDecimal("170"), new BigDecimal("70"),
                BodyMassIndex.ADULT_FROM_AGE_YEARS);
        BodyMassIndex.Bmi justBelow = BodyMassIndex.of(new BigDecimal("170"), new BigDecimal("70"),
                BodyMassIndex.ADULT_FROM_AGE_YEARS - 1);

        assertThat(justAdult.computed()).isTrue();
        assertThat(justBelow.computed()).isFalse();
    }

    @Test
    void aMissingAgeIsRefusedRatherThanAssumedAdult() {
        BodyMassIndex.Bmi result = BodyMassIndex.of(new BigDecimal("170"), new BigDecimal("70"), null);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.bmi()).isNull();
    }

    @Test
    void aMissingMeasurementNamesWhichOneIsMissing() {
        assertThat(BodyMassIndex.of(null, new BigDecimal("70"), 40).explanation())
                .startsWith("Height is");
        assertThat(BodyMassIndex.of(new BigDecimal("170"), null, 40).explanation())
                .startsWith("Weight is");
        assertThat(BodyMassIndex.of(null, null, 40).explanation())
                .startsWith("Height and weight are");
    }

    @Test
    void aHeightInMetresIsRefusedRatherThanSquaredIntoAnIndexNoBandWouldCatch() {
        // 70 / 1.7² with the height read as centimetres gives an index in the hundred-thousands.
        BodyMassIndex.Bmi result = adult("1.7", "70");

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(result.explanation()).contains("metres/centimetres");
    }

    @Test
    void aWeightInGramsIsRefused() {
        BodyMassIndex.Bmi result = adult("170", "70000");

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(result.explanation()).contains("grams/kilograms");
    }

    @Test
    void everyResultCarriesTheContentVersionThatProducedIt() {
        assertThat(adult("170", "70").contentVersion()).isEqualTo(BodyMassIndex.CONTENT_VERSION);
        assertThat(adult("1.7", "70").contentVersion()).isEqualTo(BodyMassIndex.CONTENT_VERSION);
        assertThat(BodyMassIndex.of(null, null, null).contentVersion())
                .isEqualTo(BodyMassIndex.CONTENT_VERSION);
    }
}
