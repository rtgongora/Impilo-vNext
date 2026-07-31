package zw.gov.mohcc.impilo.medicine.anthropometry;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * BSA sets cytotoxic doses. A BSA wrong by a fifth is a chemotherapy dose wrong by a fifth, in a
 * class of medicine where that margin separates marrow suppression from an ineffective cycle. Each
 * test names the harm the assertion stands between a patient and.
 */
class BodySurfaceAreaTest {

    @Test
    void theSeventyKiloOneSeventyPatientMatchesBothPublishedValues() {
        // The reference body used throughout the dosing literature: DuBois 1.81 m², Mosteller 1.82.
        var result = BodySurfaceArea.of(BigDecimal.valueOf(170d), BigDecimal.valueOf(70d));

        assertThat(result.computed()).isTrue();
        assertThat(result.duBoisM2().doubleValue()).isCloseTo(1.81d, within(0.01d));
        assertThat(result.mostellerM2().doubleValue()).isCloseTo(1.82d, within(0.01d));
        assertThat(result.reason()).isNull();
        assertThat(result.contentVersion()).isEqualTo(BodySurfaceArea.CONTENT_VERSION);
    }

    @Test
    void bothFormulaeAreReturnedSoNeitherIsSilentlyChosenForThePrescriber() {
        // The two disagree by one to three per cent, and at chemotherapy doses that is a real
        // difference in milligrams. A unit that doses on one and records the other has an audit
        // trail that does not reproduce the dose given.
        var result = BodySurfaceArea.of(BigDecimal.valueOf(170d), BigDecimal.valueOf(70d));

        assertThat(result.duBoisM2()).isNotNull();
        assertThat(result.mostellerM2()).isNotNull();
        assertThat(result.spreadM2().doubleValue()).isLessThan(0.1d);
    }

    @Test
    void theSpreadWidensAtExtremeHabitusRatherThanBeingSmoothedAway() {
        // The formulae diverge most in the patients where dosing accuracy matters most. The spread
        // is exposed so a surface can show the disagreement instead of implying consensus.
        var midRange = BodySurfaceArea.of(BigDecimal.valueOf(170d), BigDecimal.valueOf(70d));
        var extreme = BodySurfaceArea.of(BigDecimal.valueOf(150d), BigDecimal.valueOf(180d));

        assertThat(extreme.computed()).isTrue();
        assertThat(extreme.spreadM2().doubleValue())
                .isGreaterThan(midRange.spreadM2().doubleValue());
    }

    @Test
    void aHeightInMetresTypedIntoTheCentimetreFieldIsRefused() {
        // 1.7 is a plausible height in metres and an impossible one in centimetres. Left to run,
        // DuBois returns roughly 0.06 m², and the cytotoxic dose computed from it is a fraction of
        // what was intended.
        var result = BodySurfaceArea.of(BigDecimal.valueOf(1.7d), BigDecimal.valueOf(70d));

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(result.explanation()).contains("metres/centimetres");
    }

    @Test
    void aWeightInGramsTypedIntoTheKilogramFieldIsRefused() {
        var result = BodySurfaceArea.of(BigDecimal.valueOf(170d), BigDecimal.valueOf(70000d));

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
    }

    @Test
    void missingWeightOrHeightIsRefusedRatherThanDefaultedToAStandardBody() {
        // The tempting shortcut is to substitute a 1.73 m² "average adult". That is precisely how a
        // cachectic patient receives a dose calculated for someone half again their size.
        assertThat(BodySurfaceArea.of(null, BigDecimal.valueOf(70d)).reason())
                .isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(BodySurfaceArea.of(BigDecimal.valueOf(170d), null).reason())
                .isEqualTo(RefusalReason.INPUT_MISSING);

        var neither = BodySurfaceArea.of(null, null);
        assertThat(neither.duBoisM2()).isNull();
        assertThat(neither.mostellerM2()).isNull();
        assertThat(neither.spreadM2()).isNull();
    }

    @Test
    void aRefusedResultOffersNoAreaThroughAnyAccessor() {
        // A refusal that still exposes a number somewhere is a refusal a caller will route around.
        var refused = BodySurfaceArea.of(BigDecimal.valueOf(1.7d), BigDecimal.valueOf(70d));

        assertThat(refused.duBoisM2()).isNull();
        assertThat(refused.mostellerM2()).isNull();
        assertThat(refused.spreadM2()).isNull();
        assertThat(refused.explanation()).isNotBlank();
    }
}
