package zw.gov.mohcc.impilo.clinical.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.clinical.persistence.entity.MedicineGuidanceEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.MedicineGuidanceRepository;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.MedicationLine;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Wave A2 (3C partial completion) — proves the clinical-evaluate path now enriches
 * medications with the national-formulary specialistOnly designation from
 * MedicineGuidance, so the specialist-only level-of-care rule fires in production
 * (not only when a caller hand-supplies the flag).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClinicalContextEnricherTest {

    @Mock private MedicineGuidanceRepository repo;

    private ClinicalEvaluationContext ctx(MedicationLine... meds) {
        return new ClinicalEvaluationContext(
                null, null, null, "C", List.of(), List.of(meds), null, null, null);
    }

    private MedicineGuidanceEntity guidance(boolean specialistOnly) {
        MedicineGuidanceEntity m = org.mockito.Mockito.mock(MedicineGuidanceEntity.class);
        when(m.getSpecialistOnly()).thenReturn(specialistOnly);
        return m;
    }

    @Test
    void enrichesSpecialistOnlyFromGuidance() {
        var g = guidance(true);
        when(repo.search("vancomycin")).thenReturn(List.of(g));
        var enricher = new ClinicalContextEnricher(repo);

        var out = enricher.enrich(ctx(new MedicationLine("vancomycin", "Vancomycin", null, null, null, false, false)));

        assertThat(out.activeMedications().get(0).specialistOnly()).isTrue();
    }

    @Test
    void nonSpecialistMedicineStaysFalse() {
        var g = guidance(false);
        lenient().when(repo.search("amoxicillin")).thenReturn(List.of(g));
        var enricher = new ClinicalContextEnricher(repo);

        var out = enricher.enrich(ctx(new MedicationLine("amoxicillin", "Amoxicillin", null, null, null, false, false)));

        assertThat(out.activeMedications().get(0).specialistOnly()).isFalse();
    }

    @Test
    void callerSuppliedFlagIsPreserved_noLookup() {
        var enricher = new ClinicalContextEnricher(repo);
        var out = enricher.enrich(ctx(new MedicationLine("x", "X", null, null, null, false, true)));
        assertThat(out.activeMedications().get(0).specialistOnly()).isTrue();
        // no repo lookup needed when caller already flagged it
        org.mockito.Mockito.verifyNoInteractions(repo);
    }

    @Test
    void enrichedContextDrivesTheLevelOfCareGate_endToEnd() {
        var g = guidance(true);
        when(repo.search("vancomycin")).thenReturn(List.of(g));
        var enricher = new ClinicalContextEnricher(repo);
        var engine = new ClinicalRulesEngine(3);

        var enriched = enricher.enrich(ctx(new MedicationLine("vancomycin", "Vancomycin", null, null, null, false, false)));

        assertThat(engine.evaluate(enriched).stream().map(RuleAlert::code))
                .contains("LEVEL_OF_CARE_SPECIALIST_ONLY_MEDICINE");
    }
}
