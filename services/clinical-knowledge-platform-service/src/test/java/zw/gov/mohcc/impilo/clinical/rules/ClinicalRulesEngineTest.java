package zw.gov.mohcc.impilo.clinical.rules;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.MedicationLine;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalRulesEngineTest {

    private final ClinicalRulesEngine engine = new ClinicalRulesEngine(3);

    @Test
    void asthmaSabaMonotherapy_firesWhenAsthmaDxAndSabaWithoutIcs() {
        var ctx = new ClinicalEvaluationContext(
                40.0,
                null,
                70.0,
                "C",
                List.of("ASTHMA"),
                List.of(new MedicationLine("salbutamol", "Salbutamol", null, "INHALATION", 30, false)),
                null,
                null,
                null
        );
        List<RuleAlert> alerts = engine.evaluate(ctx);
        assertThat(alerts.stream().map(RuleAlert::code)).contains("ASTHMA_SABA_MONOTHERAPY");
    }

    @Test
    void gonorrhoeaLegacyCeftriaxoneDose_fires() {
        Map<String, Object> m = Map.of(
                "diagnoses", List.of("GONORRHOEA"),
                "activeMedications", List.of(Map.of(
                        "genericName", "ceftriaxone",
                        "doseMg", 250
                ))
        );
        var ctx = ClinicalEvaluationContext.fromMap(m);
        assertThat(engine.evaluate(ctx).stream().map(RuleAlert::code))
                .contains("GONORRHOEA_CEFTRIAXONE_LEGACY_DOSE");
    }

    @Test
    void stewardshipBroadSpectrumDuration_fires() {
        Map<String, Object> m = Map.of(
                "activeMedications", List.of(Map.of(
                        "genericName", "meropenem",
                        "daysOnTherapy", 5
                )),
                "empiricBroadSpectrumDays", 5,
                "cultureDocumented", true
        );
        var ctx = ClinicalEvaluationContext.fromMap(m);
        List<RuleAlert> alerts = engine.evaluate(ctx);
        assertThat(alerts.stream().map(RuleAlert::code))
                .contains("STEWARDSHIP_BROAD_SPECTRUM_DURATION", "STEWARDSHIP_DIRECT_THERAPY_HINT");
    }

    @Test
    void neonatalGentamicin_fires() {
        Map<String, Object> m = Map.of(
                "ageDays", 5,
                "activeMedications", List.of(Map.of("genericName", "gentamicin"))
        );
        var ctx = ClinicalEvaluationContext.fromMap(m);
        assertThat(engine.evaluate(ctx).stream().map(RuleAlert::code))
                .contains("NEONATAL_GENTAMICIN_SPECIALIST");
    }
}
