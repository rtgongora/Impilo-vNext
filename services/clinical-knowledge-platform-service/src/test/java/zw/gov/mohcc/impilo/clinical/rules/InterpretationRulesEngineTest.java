package zw.gov.mohcc.impilo.clinical.rules;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretationCode;
import zw.gov.mohcc.impilo.clinical.interpretation.model.InterpretedObservation;
import zw.gov.mohcc.impilo.clinical.interpretation.model.Sex;
import zw.gov.mohcc.impilo.clinical.interpretation.model.Trend;
import zw.gov.mohcc.impilo.clinical.rules.model.ClinicalEvaluationContext;
import zw.gov.mohcc.impilo.clinical.rules.model.MedicationLine;
import zw.gov.mohcc.impilo.clinical.rules.model.RuleAlert;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the interpretation-driven rules (critical-lab, hyperkalaemia, AKI, pregnancy-contra) and
 * the safety invariant that existing rules are unchanged when no interpreted observations are present.
 */
class InterpretationRulesEngineTest {

    private final ClinicalRulesEngine engine = new ClinicalRulesEngine(3);

    private static InterpretedObservation obs(String loinc, String name, String value, String unit,
                                              InterpretationCode code, Trend trend) {
        return new InterpretedObservation(loinc, null, name, "LAB",
                value == null ? null : new BigDecimal(value), unit, code, null, null, trend, "n");
    }

    private static ClinicalEvaluationContext base() {
        return new ClinicalEvaluationContext(40.0, null, 70.0, "C",
                List.of(), List.of(), null, null, null, null, List.of());
    }

    private List<String> codes(ClinicalEvaluationContext ctx) {
        return engine.evaluate(ctx).stream().map(RuleAlert::code).toList();
    }

    @Test
    void criticalLab_firesForCriticalInterpretedValue() {
        var ctx = base().withInterpretations(
                List.of(obs("2951-2", "Sodium", "118", "mmol/L", InterpretationCode.CRITICAL_LOW, null)),
                Map.of());
        assertThat(codes(ctx)).contains("CRITICAL_LAB_VALUE");
    }

    @Test
    void criticalLab_doesNotFireForNormal() {
        var ctx = base().withInterpretations(
                List.of(obs("2951-2", "Sodium", "140", "mmol/L", InterpretationCode.NORMAL, null)),
                Map.of());
        assertThat(codes(ctx)).doesNotContain("CRITICAL_LAB_VALUE");
    }

    @Test
    void hyperkalaemia_firesForHighPotassium() {
        var ctx = base().withInterpretations(
                List.of(obs("2823-3", "Potassium", "5.9", "mmol/L", InterpretationCode.HIGH, null)),
                Map.of());
        assertThat(codes(ctx)).contains("HYPERKALAEMIA");
    }

    @Test
    void hyperkalaemia_criticalAlsoTriggersCriticalLabNet() {
        var ctx = base().withInterpretations(
                List.of(obs("2823-3", "Potassium", "7.1", "mmol/L", InterpretationCode.CRITICAL_HIGH, null)),
                Map.of());
        assertThat(codes(ctx)).contains("HYPERKALAEMIA", "CRITICAL_LAB_VALUE");
    }

    @Test
    void aki_firesForRisingElevatedCreatinine() {
        var ctx = base().withInterpretations(
                List.of(obs("2160-0", "Creatinine", "180", "umol/L", InterpretationCode.HIGH, Trend.RISING)),
                Map.of());
        assertThat(codes(ctx)).contains("ACUTE_KIDNEY_INJURY_SUSPECTED");
    }

    @Test
    void aki_doesNotFireForElevatedButStableCreatinine() {
        var ctx = base().withInterpretations(
                List.of(obs("2160-0", "Creatinine", "180", "umol/L", InterpretationCode.HIGH, Trend.STABLE)),
                Map.of());
        assertThat(codes(ctx)).doesNotContain("ACUTE_KIDNEY_INJURY_SUSPECTED");
    }

    @Test
    void lowEgfr_firesRenalImpairment() {
        var ctx = base().withInterpretations(List.of(), Map.of("eGFR", 42.0));
        assertThat(codes(ctx)).contains("RENAL_IMPAIRMENT_LOW_EGFR");
    }

    @Test
    void pregnancyContraindicatedDrug_firesForTeratogenWhenPregnant() {
        var ctx = new ClinicalEvaluationContext(28.0, null, 65.0, "C",
                List.of(),
                List.of(new MedicationLine("warfarin", "Warfarin", 5.0, "ORAL", 10, false, false)),
                null, null, null, null, List.of(),
                Sex.FEMALE, true, 12, List.of(), Map.of());
        var alerts = engine.evaluate(ctx);
        assertThat(alerts.stream().map(RuleAlert::code)).contains("PREGNANCY_CONTRAINDICATED_DRUG");
        assertThat(alerts.stream().filter(a -> a.code().equals("PREGNANCY_CONTRAINDICATED_DRUG"))
                .findFirst().orElseThrow().severity()).isEqualTo("CRITICAL");
    }

    @Test
    void pregnancyContraindicatedDrug_doesNotFireWhenNotPregnant() {
        var ctx = new ClinicalEvaluationContext(28.0, null, 65.0, "C",
                List.of(),
                List.of(new MedicationLine("warfarin", "Warfarin", 5.0, "ORAL", 10, false, false)),
                null, null, null, null, List.of(),
                Sex.FEMALE, false, null, List.of(), Map.of());
        assertThat(codes(ctx)).doesNotContain("PREGNANCY_CONTRAINDICATED_DRUG");
    }

    @Test
    void existingRules_unchanged_whenNoInterpretedObservations() {
        // Safety regression: an asthma SABA-monotherapy context still fires and emits no interpretation alerts.
        var ctx = new ClinicalEvaluationContext(40.0, null, 70.0, "C",
                List.of("ASTHMA"),
                List.of(new MedicationLine("salbutamol", "Salbutamol", null, "INHALATION", 30, false, false)),
                null, null, null, null, List.of());
        var codes = codes(ctx);
        assertThat(codes).contains("ASTHMA_SABA_MONOTHERAPY");
        assertThat(codes).doesNotContain("CRITICAL_LAB_VALUE", "HYPERKALAEMIA",
                "ACUTE_KIDNEY_INJURY_SUSPECTED", "PREGNANCY_CONTRAINDICATED_DRUG");
    }
}
