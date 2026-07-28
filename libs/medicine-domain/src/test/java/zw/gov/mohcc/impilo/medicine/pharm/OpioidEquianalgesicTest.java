package zw.gov.mohcc.impilo.medicine.pharm;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opioid conversion errors kill people, and they do it quietly: the wrong number looks like a
 * perfectly ordinary dose. Every test below names the specific way a patient would be harmed if the
 * assertion stopped holding.
 */
class OpioidEquianalgesicTest {

    @Test
    void morphineIsItsOwnEquivalent() {
        var result = OpioidEquianalgesic.oralMorphineEquivalent(
                OpioidEquianalgesic.Opioid.ORAL_MORPHINE, BigDecimal.valueOf(60));

        assertThat(result.computed()).isTrue();
        assertThat(result.oralMorphineEquivalentMgPer24h()).isEqualByComparingTo("60.0");
        assertThat(result.factorApplied()).isEqualTo(1.0d);
    }

    @Test
    void codeineTwoHundredAndFortyIsTwentyFourMilligramsOfMorphine() {
        // The worked example for the ÷10 family. Codeine is the opioid most often thought of as
        // "weak", and a regimen of codeine 240 mg/24h plus tramadol 400 mg/24h is a real 64 mg of
        // morphine a day, which is not weak at all — the whole reason for computing a total.
        var result = OpioidEquianalgesic.oralMorphineEquivalent(
                OpioidEquianalgesic.Opioid.ORAL_CODEINE, BigDecimal.valueOf(240));

        assertThat(result.oralMorphineEquivalentMgPer24h()).isEqualByComparingTo("24.0");
        assertThat(result.opioid().source()).contains("CDC");
    }

    @Test
    void tramadolFourHundredIsFortyMilligramsOfMorphine() {
        var result = OpioidEquianalgesic.oralMorphineEquivalent(
                OpioidEquianalgesic.Opioid.ORAL_TRAMADOL, BigDecimal.valueOf(400));

        assertThat(result.oralMorphineEquivalentMgPer24h()).isEqualByComparingTo("40.0");
    }

    @Test
    void oxycodoneTwentyIsThirtyMilligramsOfMorphine() {
        // Oxycodone is the one drug in the table where the factor is greater than 1. Applying a
        // ÷ instead of a × here understates a patient's burden by a factor of 2.25.
        var result = OpioidEquianalgesic.oralMorphineEquivalent(
                OpioidEquianalgesic.Opioid.ORAL_OXYCODONE, BigDecimal.valueOf(20));

        assertThat(result.oralMorphineEquivalentMgPer24h()).isEqualByComparingTo("30.0");
    }

    @Test
    void aTwentyFiveMicrogramPatchIsNinetyMilligramsOfMorphineADay() {
        // The published anchor for transdermal fentanyl. Note the unit: micrograms per HOUR, for a
        // dose expressed per 24 hours. Everything else in the table is mg/24h, so the unit has to
        // travel with the drug or a patch strength gets multiplied as if it were a daily dose.
        var result = OpioidEquianalgesic.oralMorphineEquivalent(
                OpioidEquianalgesic.Opioid.TRANSDERMAL_FENTANYL, BigDecimal.valueOf(25));

        assertThat(result.oralMorphineEquivalentMgPer24h()).isEqualByComparingTo("90.0");
        assertThat(OpioidEquianalgesic.Opioid.TRANSDERMAL_FENTANYL.doseUnit()).isEqualTo("microgram/hour");
        assertThat(OpioidEquianalgesic.Opioid.TRANSDERMAL_FENTANYL.source()).contains("range");
    }

    @Test
    void methadoneHasNoLinearFactorAndIsRefusedRatherThanApproximated() {
        // The most important test in this library. Methadone's morphine ratio rises with the dose
        // being converted from — roughly 4:1 low down, 20:1 or more above 1000 mg/day — and it
        // accumulates over days because of its long, variable half-life. Any single factor is badly
        // wrong at one end of the range and lethal at the other, and the failure is delayed by
        // days, so nobody connects the death to the conversion. A refusal that routes the
        // prescriber to specialist advice is the correct output, and it must never be "improved".
        var result = OpioidEquianalgesic.oralMorphineEquivalent(
                OpioidEquianalgesic.Opioid.ORAL_METHADONE, BigDecimal.valueOf(30));

        assertThat(result.computed()).isFalse();
        assertThat(result.oralMorphineEquivalentMgPer24h()).isNull();
        assertThat(result.reason()).isEqualTo(RefusalReason.REQUIRES_SPECIALIST_CALCULATION);
        assertThat(result.explanation()).contains("specialist");
        assertThat(OpioidEquianalgesic.Opioid.ORAL_METHADONE.oralMorphineFactor()).isNull();
        assertThat(OpioidEquianalgesic.Opioid.ORAL_METHADONE.linearlyConvertible()).isFalse();
    }

    @Test
    void methadoneIsTheOnlyDrugWithoutAFactorAndEveryOtherFactorIsPositive() {
        // A factor of zero on any drug would silently drop it from every total; a negative one
        // would reduce the total. Asserted across the whole enum so a new drug added later cannot
        // arrive with an unset factor and be treated as convertible.
        for (var opioid : OpioidEquianalgesic.Opioid.values()) {
            if (opioid == OpioidEquianalgesic.Opioid.ORAL_METHADONE) {
                assertThat(opioid.oralMorphineFactor()).isNull();
            } else {
                assertThat(opioid.oralMorphineFactor()).isNotNull().isGreaterThan(0.0d);
            }
            assertThat(opioid.doseUnit()).isNotBlank();
            assertThat(opioid.source()).isNotBlank();
            assertThat(opioid.implausibleDoseCeiling().signum()).isPositive();
        }
    }

    @Test
    void aMissingDoseIsNotADoseOfZero() {
        // An opioid on the list with no recorded dose is an incomplete medication record, not a
        // patient taking none of it. Scoring it as zero understates the total in the direction that
        // makes an unsafe regimen look safe.
        var result = OpioidEquianalgesic.oralMorphineEquivalent(
                OpioidEquianalgesic.Opioid.ORAL_MORPHINE, null);

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(result.explanation()).contains("not a dose of zero");
    }

    @Test
    void anUnidentifiedOpioidIsRefused() {
        var result = OpioidEquianalgesic.oralMorphineEquivalent(null, BigDecimal.valueOf(60));

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
    }

    @Test
    void aDoseAboveThePlausibilityCeilingIsRefusedAsATranscriptionError() {
        // Tramadol 2000 mg/24h is five times the maximum dose and is almost always a decimal slip
        // or a milligram/microgram confusion. The ceiling is a transcription guard, not a maximum
        // dose — maximum doses are prescribing policy and are deliberately not in this library.
        var result = OpioidEquianalgesic.oralMorphineEquivalent(
                OpioidEquianalgesic.Opioid.ORAL_TRAMADOL, BigDecimal.valueOf(2000));

        assertThat(result.computed()).isFalse();
        assertThat(result.reason()).isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(result.explanation()).contains("not a maximum dose");
    }

    @Test
    void aZeroOrNegativeDoseIsRefused() {
        assertThat(OpioidEquianalgesic.oralMorphineEquivalent(
                OpioidEquianalgesic.Opioid.ORAL_MORPHINE, BigDecimal.ZERO).reason())
                .isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
        assertThat(OpioidEquianalgesic.oralMorphineEquivalent(
                OpioidEquianalgesic.Opioid.ORAL_MORPHINE, BigDecimal.valueOf(-10)).reason())
                .isEqualTo(RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE);
    }

    @Test
    void aRegimenOfConvertibleOpioidsSumsToATotal() {
        // Morphine 60 mg/24h plus oxycodone 20 mg/24h is 90 mg of oral morphine equivalent — a dose
        // at which most guidance says a second opinion is worth having. Producing the total is the
        // point of the class; what to do at 90 is policy.
        Map<OpioidEquianalgesic.Opioid, BigDecimal> regimen = new LinkedHashMap<>();
        regimen.put(OpioidEquianalgesic.Opioid.ORAL_MORPHINE, BigDecimal.valueOf(60));
        regimen.put(OpioidEquianalgesic.Opioid.ORAL_OXYCODONE, BigDecimal.valueOf(20));

        var total = OpioidEquianalgesic.totalOralMorphineEquivalent(regimen);

        assertThat(total.computed()).isTrue();
        assertThat(total.complete()).isTrue();
        assertThat(total.oralMorphineEquivalentMgPer24h()).isEqualByComparingTo("90.0");
        assertThat(total.converted()).hasSize(2);
        assertThat(total.notConverted()).isEmpty();
    }

    @Test
    void aRegimenContainingMethadoneHasNoTotalAtAll() {
        // The design decision this test defends. Methadone is usually the largest contributor in
        // the regimen it appears in, so a partial sum that silently drops it understates the
        // patient's opioid burden by the most dangerous component. The total is null, the partial
        // sum is named so it cannot be mistaken for one, and the unconvertible item is returned
        // with its reason rather than disappearing.
        Map<OpioidEquianalgesic.Opioid, BigDecimal> regimen = new LinkedHashMap<>();
        regimen.put(OpioidEquianalgesic.Opioid.ORAL_MORPHINE, BigDecimal.valueOf(60));
        regimen.put(OpioidEquianalgesic.Opioid.ORAL_METHADONE, BigDecimal.valueOf(40));
        regimen.put(OpioidEquianalgesic.Opioid.ORAL_OXYCODONE, BigDecimal.valueOf(20));

        var total = OpioidEquianalgesic.totalOralMorphineEquivalent(regimen);

        assertThat(total.computed()).isFalse();
        assertThat(total.complete()).isFalse();
        assertThat(total.oralMorphineEquivalentMgPer24h()).isNull();
        assertThat(total.partialSumExcludingUnconvertedMgPer24h()).isEqualByComparingTo("90.0");
        assertThat(total.notConverted()).hasSize(1);
        assertThat(total.notConverted().get(0).opioid())
                .isEqualTo(OpioidEquianalgesic.Opioid.ORAL_METHADONE);
        assertThat(total.notConverted().get(0).reason())
                .isEqualTo(RefusalReason.REQUIRES_SPECIALIST_CALCULATION);
        assertThat(total.explanation()).contains("methadone");
        assertThat(total.explanation()).contains("must not be shown as a total");
    }

    @Test
    void anEmptyRegimenHasNoTotalRatherThanATotalOfZero() {
        // A patient with no recorded opioids and a patient known to be on none are different
        // records. A total of zero asserts the second from the first.
        var total = OpioidEquianalgesic.totalOralMorphineEquivalent(Map.of());

        assertThat(total.computed()).isFalse();
        assertThat(total.reason()).isEqualTo(RefusalReason.INPUT_MISSING);
        assertThat(OpioidEquianalgesic.totalOralMorphineEquivalent(null).computed()).isFalse();
    }

    @Test
    void thereIsNoInverseConversionOutOfMorphine() {
        // Equianalgesic tables are not symmetric — published factors differ by direction of switch
        // — and a switching dose additionally needs a 25–50% reduction for incomplete cross
        // tolerance, which is a clinical decision and not in this library. An inverse method here
        // would be read as "the dose of oxycodone to prescribe", which is an overdose.
        assertThat(OpioidEquianalgesic.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("from")
                        || method.getName().toLowerCase().contains("inverse")
                        || method.getName().toLowerCase().contains("switch"));
    }
}
