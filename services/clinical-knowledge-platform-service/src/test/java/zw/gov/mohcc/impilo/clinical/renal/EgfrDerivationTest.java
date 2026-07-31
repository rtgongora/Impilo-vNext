package zw.gov.mohcc.impilo.clinical.renal;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretationCode;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretedObservation;
import zw.gov.mohcc.impilo.clinical.interpretation.model.Sex;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EgfrDerivationTest {

    private static InterpretedObservation observation(String loinc, String name, String value, String unit) {
        return new InterpretedObservation(loinc, null, name, "LAB",
                value == null ? null : new BigDecimal(value), unit,
                InterpretationCode.NORMAL, null, null, null, null);
    }

    private static InterpretedObservation creatinine(String value, String unit) {
        return observation("2160-0", "Creatinine [Mass/volume] in Serum or Plasma", value, unit);
    }

    @Test
    void aCreatinineInMicromolesGivesAnEgfrAndItsKdigoCategory() {
        var derived = EgfrDerivation.fromObservations(List.of(creatinine("90", "umol/L")), 54, Sex.FEMALE);

        assertThat(derived.present()).isTrue();
        assertThat(derived.egfr()).isNotNull();
        assertThat(derived.kdigoStage()).isNotBlank();
        assertThat(derived.contentVersion()).isNotBlank();
        assertThat(derived.refusalReason()).isNull();
    }

    @Test
    void aMilligramPerDecilitreValueIsConvertedRatherThanRead() {
        // 1.0 mg/dL is 88.4 µmol/L. Read as µmol/L it would put a normal patient in stage 5.
        var converted = EgfrDerivation.fromObservations(
                List.of(creatinine("1.0", "mg/dL")), 54, Sex.FEMALE);
        var equivalent = EgfrDerivation.fromObservations(
                List.of(creatinine("88.4", "umol/L")), 54, Sex.FEMALE);

        assertThat(converted.present()).isTrue();
        assertThat(converted.egfr().doubleValue())
                .isCloseTo(equivalent.egfr().doubleValue(), within(0.5));
    }

    @Test
    void anUnrecognisedUnitIsSkippedRatherThanAssumed() {
        var derived = EgfrDerivation.fromObservations(
                List.of(creatinine("11.3", "mg/L")), 54, Sex.FEMALE);

        // Reading 11.3 mg/L as µmol/L would report a spectacular filtration rate for a patient in
        // renal failure. Refusing to guess the unit is the only safe answer.
        assertThat(derived.present()).isFalse();
        assertThat(derived.refusalReason()).isNull();
    }

    @Test
    void creatinineClearanceAndTheProteinRatioAreNotMistakenForACreatinine() {
        var clearance = EgfrDerivation.fromObservations(
                List.of(observation(null, "Creatinine clearance", "80", "mL/min")), 54, Sex.FEMALE);
        var ratio = EgfrDerivation.fromObservations(
                List.of(observation(null, "Protein/creatinine ratio", "35", "mg/mmol")), 54, Sex.FEMALE);

        assertThat(clearance.present()).isFalse();
        assertThat(ratio.present()).isFalse();
    }

    @Test
    void anUnnamedCreatinineIsStillFoundByItsLoincCode() {
        var derived = EgfrDerivation.fromObservations(
                List.of(observation("2160-0", null, "90", "umol/L")), 54, Sex.MALE);

        assertThat(derived.present()).isTrue();
    }

    @Test
    void anUnknownSexProducesNothingBecauseTheEquationHasNoNeutralCoefficient() {
        var derived = EgfrDerivation.fromObservations(
                List.of(creatinine("90", "umol/L")), 54, Sex.UNKNOWN);

        assertThat(derived.present()).isFalse();
        // Not even attempted: a defaulted sex moves the rate by roughly ten per cent, which is
        // enough to cross a KDIGO boundary and change a dose.
        assertThat(derived.refusalReason()).isNull();
    }

    @Test
    void aMissingAgeProducesNothing() {
        var derived = EgfrDerivation.fromObservations(
                List.of(creatinine("90", "umol/L")), null, Sex.FEMALE);

        assertThat(derived.present()).isFalse();
    }

    @Test
    void noCreatinineProducesNothing() {
        var noObservations = EgfrDerivation.fromObservations(List.of(), 54, Sex.FEMALE);
        var otherAnalyte = EgfrDerivation.fromObservations(
                List.of(observation("2951-2", "Sodium", "138", "mmol/L")), 54, Sex.FEMALE);

        assertThat(noObservations.present()).isFalse();
        assertThat(otherAnalyte.present()).isFalse();
        assertThat(EgfrDerivation.fromObservations(null, 54, Sex.FEMALE).present()).isFalse();
    }

    @Test
    void anImplausibleCreatinineIsRefusedByNameRatherThanScored() {
        // A creatinine of 1.1 is a mg/dL value entered as µmol/L. The calculator says so, and the
        // reason travels back so a caller can log why no rate appeared.
        var derived = EgfrDerivation.fromCreatinine(new BigDecimal("1.1"), 54,
                zw.gov.mohcc.impilo.medicine.common.Sex.FEMALE);

        assertThat(derived.present()).isFalse();
        assertThat(derived.refusalReason()).isNotBlank();
        assertThat(derived.refusalDetail()).isNotBlank();
    }

    @Test
    void theFirstUsableCreatinineIsTakenAndAnUnusableOneDoesNotBlockIt() {
        var derived = EgfrDerivation.fromObservations(
                List.of(creatinine(null, "umol/L"), creatinine("90", "umol/L")), 54, Sex.FEMALE);

        assertThat(derived.present()).isTrue();
    }

    @Test
    void aDerivedValueSurvivesAsADoubleForTheMapsThatCarryDoubles() {
        var derived = EgfrDerivation.fromObservations(List.of(creatinine("90", "umol/L")), 54, Sex.FEMALE);

        assertThat(derived.asDouble()).isEqualTo(derived.egfr().doubleValue());
        assertThat(EgfrDerivation.fromObservations(List.of(), 54, Sex.FEMALE).asDouble()).isNull();
    }
}
