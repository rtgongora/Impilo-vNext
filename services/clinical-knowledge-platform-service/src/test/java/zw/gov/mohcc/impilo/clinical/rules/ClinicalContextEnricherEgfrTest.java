package zw.gov.mohcc.impilo.clinical.rules;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretationCode;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretedObservation;
import zw.gov.mohcc.impilo.clinical.interpretation.model.Sex;
import zw.gov.mohcc.impilo.clinical.persistence.repository.MedicineGuidanceRepository;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The renal-impairment rule fires for a patient whose clinic measured a creatinine.
 *
 * <p>Before this, {@code RENAL_IMPAIRMENT_LOW_EGFR} was keyed on a {@code derivedValues["egfr"]}
 * that nothing in the platform produced. It fired only when a calling system happened to send one,
 * which meant it was silent for the ordinary Zimbabwean clinic that measures creatinine and leaves
 * the filtration rate to be worked out. The rule was never wrong; it was never fed.</p>
 */
class ClinicalContextEnricherEgfrTest {

    private final MedicineGuidanceRepository guidance = mock(MedicineGuidanceRepository.class);
    private final ClinicalRulesEngine engine = new ClinicalRulesEngine(3);
    private ClinicalContextEnricher enricher;

    private ClinicalContextEnricher enricher() {
        if (enricher == null) {
            when(guidance.search(anyString())).thenReturn(List.of());
            enricher = new ClinicalContextEnricher(guidance);
        }
        return enricher;
    }

    private static InterpretedObservation creatinine(String value, String unit) {
        return new InterpretedObservation("2160-0", null, "Creatinine", "LAB",
                new BigDecimal(value), unit, InterpretationCode.HIGH, null, null, null, "n");
    }

    private static ClinicalEvaluationContext context(Double ageYears, Sex sex,
                                                     List<InterpretedObservation> observations,
                                                     Map<String, Double> derived) {
        return new ClinicalEvaluationContext(ageYears, null, 70.0, "C", List.of(), List.of(),
                null, null, null, null, List.of(), sex, null, null, observations, derived);
    }

    private List<String> codes(ClinicalEvaluationContext ctx) {
        return engine.evaluate(enricher().enrich(ctx)).stream().map(RuleAlert::code).toList();
    }

    @Test
    void aRaisedCreatinineNowProducesAnEgfrAndFiresTheRenalRule() {
        // A 72-year-old woman with a creatinine of 190 µmol/L is well under 60 mL/min/1.73m².
        var ctx = context(72.0, Sex.FEMALE, List.of(creatinine("190", "umol/L")), Map.of());

        var enriched = enricher().enrich(ctx);

        assertThat(enriched.derivedValues()).containsKey("egfr");
        assertThat(enriched.derivedValues().get("egfr")).isLessThan(60.0);
        assertThat(codes(ctx)).contains("RENAL_IMPAIRMENT_LOW_EGFR");
    }

    @Test
    void aNormalCreatinineDerivesAnEgfrThatCorrectlyDoesNotFire() {
        var ctx = context(30.0, Sex.MALE, List.of(creatinine("85", "umol/L")), Map.of());

        var enriched = enricher().enrich(ctx);

        assertThat(enriched.derivedValues().get("egfr")).isGreaterThan(60.0);
        assertThat(codes(ctx)).doesNotContain("RENAL_IMPAIRMENT_LOW_EGFR");
    }

    @Test
    void aSuppliedEgfrIsNeverOverwrittenByADerivedOne() {
        // The laboratory's own figure may come from an equation this platform does not implement.
        // A creatinine that would derive something quite different must not displace it.
        var ctx = context(72.0, Sex.FEMALE, List.of(creatinine("190", "umol/L")),
                Map.of("egfr", 88.0));

        assertThat(enricher().enrich(ctx).derivedValues()).containsEntry("egfr", 88.0);
    }

    @Test
    void aSuppliedEgfrIsRecognisedWhateverItsCasingSoNoSecondEntryIsWritten() {
        // fromMap preserves the caller's casing and the engine looks up case-insensitively. A
        // derivation that missed "eGFR" would write a competing "egfr" alongside it.
        var ctx = context(72.0, Sex.FEMALE, List.of(creatinine("190", "umol/L")),
                Map.of("eGFR", 88.0));

        var enriched = enricher().enrich(ctx);

        assertThat(enriched.derivedValues()).hasSize(1).containsEntry("eGFR", 88.0);
    }

    @Test
    void withoutASexNothingIsDerivedAndTheRuleStaysSilentRatherThanGuessing() {
        var ctx = context(72.0, Sex.UNKNOWN, List.of(creatinine("190", "umol/L")), Map.of());

        assertThat(enricher().enrich(ctx).derivedValues()).doesNotContainKey("egfr");
        assertThat(codes(ctx)).doesNotContain("RENAL_IMPAIRMENT_LOW_EGFR");
    }

    @Test
    void withoutAnAgeNothingIsDerived() {
        var ctx = context(null, Sex.FEMALE, List.of(creatinine("190", "umol/L")), Map.of());

        assertThat(enricher().enrich(ctx).derivedValues()).doesNotContainKey("egfr");
    }

    @Test
    void anImplausibleCreatinineLeavesTheEgfrAbsentRatherThanScoringOnIt() {
        // 1.1 is a mg/dL value entered as µmol/L. Deriving from it would report a filtration rate
        // in the thousands; refusing leaves the rule unable to assess, which is the truth.
        var ctx = context(72.0, Sex.FEMALE, List.of(creatinine("1.1", "umol/L")), Map.of());

        assertThat(enricher().enrich(ctx).derivedValues()).doesNotContainKey("egfr");
    }

    @Test
    void enrichmentNeverDropsTheInterpretationsItWasGiven() {
        var observations = List.of(creatinine("190", "umol/L"));
        var ctx = context(72.0, Sex.FEMALE, observations, Map.of());

        var enriched = enricher().enrich(ctx);

        assertThat(enriched.interpretedObservations()).isEqualTo(observations);
        assertThat(enriched.sex()).isEqualTo(Sex.FEMALE);
        assertThat(enriched.ageYears()).isEqualTo(72.0);
    }

    @Test
    void aContextWithNoObservationsIsUnchanged() {
        var ctx = context(72.0, Sex.FEMALE, List.of(), Map.of());

        assertThat(enricher().enrich(ctx).derivedValues()).isEmpty();
    }
}
