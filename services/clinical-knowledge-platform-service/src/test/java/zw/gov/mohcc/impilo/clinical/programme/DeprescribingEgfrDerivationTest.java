package zw.gov.mohcc.impilo.clinical.programme;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MedicationFactDeriver;
import zw.gov.mohcc.impilo.clinical.multimorbidity.MedicineClassifier;
import zw.gov.mohcc.impilo.clinical.renal.EgfrDerivation;
import zw.gov.mohcc.impilo.clinical.rules.tabular.RuleContentLoader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The metformin stop fires for a patient whose clinic measured a creatinine and never computed a
 * rate.
 *
 * <p>{@code DEPRX_METFORMIN_STOP_EGFR_LT_30} exists to catch one specific, avoidable and frequently
 * fatal harm: metformin accumulating in a failing kidney toward lactic acidosis. It is gated on an
 * {@code egfr} fact that nothing in the estate produced, so for the ordinary patient — creatinine
 * measured, filtration rate never worked out — the rule reported the input unassessed and said
 * nothing. Correct on missing data, and useless in the clinic it was written for.</p>
 *
 * <p>The rule's own no-fire-on-absent-eGFR behaviour is unchanged and is asserted here too, because
 * that is the property a derivation is most likely to break.</p>
 */
class DeprescribingEgfrDerivationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProgrammeGuidanceService service = new ProgrammeGuidanceService(
            new RuleContentLoader(objectMapper),
            new MedicationFactDeriver(new MedicineClassifier(objectMapper)));

    private static Map<String, Object> facts(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i].toString(), kv[i + 1]);
        }
        return m;
    }

    private List<String> codes(Map<String, Object> facts) {
        return service.evaluate("DEPRESCRIBING", facts).alerts().stream()
                .map(ProgrammeGuidanceService.ProgrammeAlert::code).toList();
    }

    /** A creatinine that puts a 68-year-old woman well below 30 mL/min/1.73m². */
    private static Map<String, Object> onMetforminInSevereRenalImpairment() {
        return facts("currentMedicationCodes", List.of("metformin"),
                "serumCreatinineUmolL", 420,
                "ageYears", 68, "sex", "FEMALE");
    }

    @Test
    void aCreatinineIsEnoughToFireTheMetforminStop() {
        assertThat(codes(onMetforminInSevereRenalImpairment()))
                .contains("DEPRX_METFORMIN_STOP_EGFR_LT_30");
    }

    @Test
    void theDerivedRateIsLabelledAsDerivedRatherThanPassedOffAsALaboratoryValue() {
        var assessment = service.evaluate("DEPRESCRIBING", onMetforminInSevereRenalImpairment());

        assertThat(assessment.factProvenance())
                .containsEntry(EgfrDerivation.EGFR_KEY, EgfrDerivation.DERIVED_SOURCE);
    }

    @Test
    void theMedicationProvenanceFromTheMedicineListSurvivesAlongsideIt() {
        // The eGFR provenance is merged into the medication deriver's map, not substituted for it.
        var assessment = service.evaluate("DEPRESCRIBING", onMetforminInSevereRenalImpairment());

        assertThat(assessment.factProvenance())
                .containsEntry("onMetformin", MedicationFactDeriver.DERIVED)
                .containsKey(EgfrDerivation.EGFR_KEY);
    }

    @Test
    void aGoodCreatinineDerivesARateThatCorrectlyDoesNotFireTheStop() {
        assertThat(codes(facts("currentMedicationCodes", List.of("metformin"),
                "serumCreatinineUmolL", 70, "ageYears", 45, "sex", "FEMALE")))
                .doesNotContain("DEPRX_METFORMIN_STOP_EGFR_LT_30");
    }

    @Test
    void withNoCreatinineTheRuleStillDeclinesToScoreAndSaysSo() {
        // The property the derivation is most likely to break: silence must stay silence, and the
        // missing input must still be reported so the clinician is asked rather than reassured.
        var assessment = service.evaluate("DEPRESCRIBING",
                facts("currentMedicationCodes", List.of("metformin"), "ageYears", 68, "sex", "FEMALE"));

        assertThat(assessment.alerts().stream().map(ProgrammeGuidanceService.ProgrammeAlert::code))
                .doesNotContain("DEPRX_METFORMIN_STOP_EGFR_LT_30");
        assertThat(assessment.notAssessed()).contains(EgfrDerivation.EGFR_KEY);
        assertThat(assessment.factProvenance()).doesNotContainKey(EgfrDerivation.EGFR_KEY);
    }

    @Test
    void aSuppliedEgfrIsNotDisplacedByOneDerivedFromACreatinine() {
        // The laboratory reported 80; the creatinine would derive something under 30. The rule must
        // follow the laboratory, and no derived-provenance marker should appear.
        var assessment = service.evaluate("DEPRESCRIBING",
                facts("currentMedicationCodes", List.of("metformin"),
                        "egfr", 80, "serumCreatinineUmolL", 420,
                        "ageYears", 68, "sex", "FEMALE"));

        assertThat(assessment.alerts().stream().map(ProgrammeGuidanceService.ProgrammeAlert::code))
                .doesNotContain("DEPRX_METFORMIN_STOP_EGFR_LT_30");
        assertThat(assessment.factProvenance()).doesNotContainKey(EgfrDerivation.EGFR_KEY);
    }

    @Test
    void anUnknownSexLeavesTheRateAbsentRatherThanGuessingACoefficient() {
        var assessment = service.evaluate("DEPRESCRIBING",
                facts("currentMedicationCodes", List.of("metformin"),
                        "serumCreatinineUmolL", 420, "ageYears", 68));

        assertThat(assessment.notAssessed()).contains(EgfrDerivation.EGFR_KEY);
    }

    @Test
    void aNonNumericCreatinineIsIgnoredRatherThanFailingTheWholeAssessment() {
        var assessment = service.evaluate("DEPRESCRIBING",
                facts("currentMedicationCodes", List.of("metformin"),
                        "serumCreatinineUmolL", "not recorded", "ageYears", 68, "sex", "FEMALE"));

        assertThat(assessment.assessed()).isTrue();
        assertThat(assessment.notAssessed()).contains(EgfrDerivation.EGFR_KEY);
    }

    @Test
    void theDerivationAlsoFeedsTheOtherRenalGatesInThePack() {
        // Four rules share the eGFR fact. Feeding one and not the others would be an odd partial fix.
        assertThat(codes(facts("currentMedicationCodes", List.of("ibuprofen"),
                "serumCreatinineUmolL", 420, "ageYears", 68, "sex", "FEMALE")))
                .contains("DEPRX_NSAID_AVOID_EGFR_LT_30");
    }
}
