package zw.gov.mohcc.impilo.clinical.interpretation;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretationCode;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretationContext;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretedObservation;
import zw.gov.mohcc.impilo.clinical.interpretation.model.ObservationInput;
import zw.gov.mohcc.impilo.clinical.interpretation.model.QualifiedInterval;
import zw.gov.mohcc.impilo.clinical.interpretation.model.Sex;
import zw.gov.mohcc.impilo.clinical.interpretation.model.Trend;
import zw.gov.mohcc.impilo.clinical.terminology.UcumUnitConverter;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InterpretationEngine}: each classification band incl. critical, the
 * NO_REFERENCE_RANGE-never-NORMAL invariant, UNIT_MISMATCH, DATA_QUALITY, delta/trend, and FHIR codes.
 */
class InterpretationEngineTest {

    private final UcumUnitConverter ucum = new UcumUnitConverter();

    // Serum potassium reference 3.5–5.1 mmol/L, critical <=2.5 / >=6.5.
    private static final QualifiedInterval K_INTERVAL = new QualifiedInterval(
            bd("3.5"), bd("5.1"), "mmol/L", bd("2.5"), bd("6.5"), Sex.UNKNOWN,
            null, null, false, null, null, null, "reference", "art-K", "1.0.0",
            "http://impilo.health.zw/ObservationDefinition/potassium");

    private InterpretationEngine engineWith(List<QualifiedInterval> intervals) {
        RangeResolver resolver = new RangeResolver((loinc, local) -> intervals, ucum, false);
        return new InterpretationEngine(resolver, ucum);
    }

    private static ObservationInput k(BigDecimal value) {
        return new ObservationInput("2823-3", null, "Potassium", "LAB", value, "mmol/L",
                null, null, null, null, null, null, null, null, null, 0d);
    }

    private static InterpretationContext adult() {
        return new InterpretationContext(40.0, null, Sex.MALE, null, null, null);
    }

    @Test
    void normal_withinRange() {
        InterpretedObservation r = engineWith(List.of(K_INTERVAL)).interpret(k(bd("4.2")), adult());
        assertThat(r.code()).isEqualTo(InterpretationCode.NORMAL);
        assertThat(r.code().fhirCode()).isEqualTo("N");
        assertThat(r.range().source().name()).isEqualTo("OBSERVATION_DEFINITION");
    }

    @Test
    void low_belowLowBound() {
        InterpretedObservation r = engineWith(List.of(K_INTERVAL)).interpret(k(bd("3.0")), adult());
        assertThat(r.code()).isEqualTo(InterpretationCode.LOW);
        assertThat(r.code().fhirCode()).isEqualTo("L");
        assertThat(r.code().isAbnormal()).isTrue();
    }

    @Test
    void high_aboveHighBound() {
        InterpretedObservation r = engineWith(List.of(K_INTERVAL)).interpret(k(bd("5.8")), adult());
        assertThat(r.code()).isEqualTo(InterpretationCode.HIGH);
        assertThat(r.code().fhirCode()).isEqualTo("H");
    }

    @Test
    void criticalLow_atOrBelowCriticalLow() {
        InterpretedObservation r = engineWith(List.of(K_INTERVAL)).interpret(k(bd("2.4")), adult());
        assertThat(r.code()).isEqualTo(InterpretationCode.CRITICAL_LOW);
        assertThat(r.code().fhirCode()).isEqualTo("LL");
        assertThat(r.code().isCritical()).isTrue();
    }

    @Test
    void criticalHigh_atOrAboveCriticalHigh() {
        InterpretedObservation r = engineWith(List.of(K_INTERVAL)).interpret(k(bd("6.5")), adult());
        assertThat(r.code()).isEqualTo(InterpretationCode.CRITICAL_HIGH);
        assertThat(r.code().fhirCode()).isEqualTo("HH");
    }

    @Test
    void boundaryValues_areNormal() {
        assertThat(engineWith(List.of(K_INTERVAL)).interpret(k(bd("3.5")), adult()).code())
                .isEqualTo(InterpretationCode.NORMAL); // == low
        assertThat(engineWith(List.of(K_INTERVAL)).interpret(k(bd("5.1")), adult()).code())
                .isEqualTo(InterpretationCode.NORMAL); // == high
    }

    @Test
    void noReferenceRange_isNeverNormal() {
        InterpretedObservation r = engineWith(List.of()).interpret(k(bd("4.2")), adult());
        assertThat(r.code()).isEqualTo(InterpretationCode.NO_REFERENCE_RANGE);
        assertThat(r.code()).isNotEqualTo(InterpretationCode.NORMAL);
        assertThat(r.code().fhirCode()).isNull();
        assertThat(r.range()).isNull();
    }

    @Test
    void unitMismatch_whenIntervalNotConvertible() {
        var badUnit = new QualifiedInterval(bd("3.5"), bd("5.1"), "g/dL", null, null, Sex.UNKNOWN,
                null, null, false, null, null, null, "reference", "a", "1", "u");
        InterpretedObservation r = engineWith(List.of(badUnit)).interpret(k(bd("4.2")), adult());
        assertThat(r.code()).isEqualTo(InterpretationCode.UNIT_MISMATCH);
        assertThat(r.range()).isNull();
    }

    @Test
    void dataQuality_implausibleValue_notCritical() {
        ObservationInput implausible = new ObservationInput("2823-3", null, "Potassium", "LAB",
                bd("999"), "mmol/L", null, null, null, null, null, null, null,
                bd("1.0"), bd("9.0"), 0d); // plausible 1-9
        InterpretedObservation r = engineWith(List.of(K_INTERVAL)).interpret(implausible, adult());
        assertThat(r.code()).isEqualTo(InterpretationCode.DATA_QUALITY);
        assertThat(r.code().isCritical()).isFalse();
    }

    @Test
    void delta_and_trend_rising() {
        ObservationInput withPrior = new ObservationInput("2823-3", null, "Potassium", "LAB",
                bd("5.0"), "mmol/L", null, bd("4.0"), "mmol/L", null, null, null, null, null, null, 0d);
        InterpretedObservation r = engineWith(List.of(K_INTERVAL)).interpret(withPrior, adult());
        assertThat(r.delta()).isEqualByComparingTo("1.0");
        assertThat(r.trend()).isEqualTo(Trend.RISING);
    }

    @Test
    void delta_and_trend_falling_withUnitConversion() {
        // prior 5000 umol/L == 5.0 mmol/L; current 4.0 mmol/L → delta -1.0, FALLING.
        ObservationInput withPrior = new ObservationInput("2823-3", null, "Potassium", "LAB",
                bd("4.0"), "mmol/L", null, bd("5000"), "umol/L", null, null, null, null, null, null, 0d);
        InterpretedObservation r = engineWith(List.of(K_INTERVAL)).interpret(withPrior, adult());
        assertThat(r.delta().doubleValue()).isEqualTo(-1.0, org.assertj.core.api.Assertions.within(1e-6));
        assertThat(r.trend()).isEqualTo(Trend.FALLING);
    }

    @Test
    void trend_stable_whenNoChange() {
        ObservationInput withPrior = new ObservationInput("2823-3", null, "Potassium", "LAB",
                bd("4.0"), "mmol/L", null, bd("4.0"), "mmol/L", null, null, null, null, null, null, 0d);
        InterpretedObservation r = engineWith(List.of(K_INTERVAL)).interpret(withPrior, adult());
        assertThat(r.trend()).isEqualTo(Trend.STABLE);
    }

    @Test
    void noPrior_noDeltaOrTrend() {
        InterpretedObservation r = engineWith(List.of(K_INTERVAL)).interpret(k(bd("4.2")), adult());
        assertThat(r.delta()).isNull();
        assertThat(r.trend()).isNull();
    }

    @Test
    void missingValue_notInterpretedAsNormal() {
        ObservationInput noVal = new ObservationInput("2823-3", null, "Potassium", "LAB",
                null, "mmol/L", null, null, null, null, null, null, null, null, null, 0d);
        InterpretedObservation r = engineWith(List.of(K_INTERVAL)).interpret(noVal, adult());
        assertThat(r.code()).isEqualTo(InterpretationCode.NO_REFERENCE_RANGE);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
