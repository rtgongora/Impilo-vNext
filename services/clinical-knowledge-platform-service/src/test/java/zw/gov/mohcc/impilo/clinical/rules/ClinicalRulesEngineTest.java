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
                List.of(new MedicationLine("salbutamol", "Salbutamol", null, "INHALATION", 30, false, false)),
                null,
                null,
                null,
                null,
                List.of()
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

    // ── Specialist-only level-of-care gating (Block 3C) ──────────────────────

    @Test
    void specialistOnlyMedicineBelowSpecialistFacility_firesLevelOfCareGate() {
        Map<String, Object> m = Map.of(
                "facilityLevel", "C",
                "activeMedications", List.of(Map.of(
                        "genericName", "vancomycin",
                        "displayName", "Vancomycin",
                        "specialistOnly", true
                ))
        );
        var ctx = ClinicalEvaluationContext.fromMap(m);
        assertThat(engine.evaluate(ctx).stream().map(RuleAlert::code))
                .contains("LEVEL_OF_CARE_SPECIALIST_ONLY_MEDICINE");
    }

    @Test
    void specialistOnlyMedicineAtSpecialistFacility_doesNotFire() {
        Map<String, Object> m = Map.of(
                "facilityLevel", "S",
                "activeMedications", List.of(Map.of(
                        "genericName", "vancomycin",
                        "specialistOnly", true
                ))
        );
        var ctx = ClinicalEvaluationContext.fromMap(m);
        assertThat(engine.evaluate(ctx).stream().map(RuleAlert::code))
                .doesNotContain("LEVEL_OF_CARE_SPECIALIST_ONLY_MEDICINE");
    }

    @Test
    void nonSpecialistMedicineBelowSpecialistFacility_doesNotFire() {
        Map<String, Object> m = Map.of(
                "facilityLevel", "C",
                "activeMedications", List.of(Map.of(
                        "genericName", "amoxicillin",
                        "specialistOnly", false
                ))
        );
        var ctx = ClinicalEvaluationContext.fromMap(m);
        assertThat(engine.evaluate(ctx).stream().map(RuleAlert::code))
                .doesNotContain("LEVEL_OF_CARE_SPECIALIST_ONLY_MEDICINE");
    }

    @Test
    void specialistOnlyDefaultsFalseWhenNotSupplied_noFalsePositive() {
        Map<String, Object> m = Map.of(
                "facilityLevel", "C",
                "activeMedications", List.of(Map.of("genericName", "amoxicillin"))
        );
        var ctx = ClinicalEvaluationContext.fromMap(m);
        assertThat(ctx.activeMedications().get(0).specialistOnly()).isFalse();
        assertThat(engine.evaluate(ctx).stream().map(RuleAlert::code))
                .doesNotContain("LEVEL_OF_CARE_SPECIALIST_ONLY_MEDICINE");
    }

    // ── Drug–allergy interaction ─────────────────────────────────────────────

    @Test
    void drugAllergy_firesWhenActiveMedMatchesDocumentedAllergy() {
        Map<String, Object> m = Map.of(
                "activeMedications", List.of(Map.of("genericName", "amoxicillin", "displayName", "Amoxicillin")),
                "allergies", List.of(Map.of("substance", "amoxicillin", "severity", "SEVERE"))
        );
        var ctx = ClinicalEvaluationContext.fromMap(m);
        assertThat(engine.evaluate(ctx).stream().map(RuleAlert::code))
                .contains("DRUG_ALLERGY_INTERACTION", "SEVERE_ALLERGY_FLAG");
    }

    @Test
    void drugAllergy_doesNotFireWhenNoOverlap() {
        Map<String, Object> m = Map.of(
                "activeMedications", List.of(Map.of("genericName", "paracetamol")),
                "allergies", List.of(Map.of("substance", "penicillin", "severity", "MILD"))
        );
        var ctx = ClinicalEvaluationContext.fromMap(m);
        assertThat(engine.evaluate(ctx).stream().map(RuleAlert::code))
                .doesNotContain("DRUG_ALLERGY_INTERACTION", "SEVERE_ALLERGY_FLAG");
    }

    // ── Vital-sign thresholds ────────────────────────────────────────────────

    @Test
    void vitals_hypertensiveCrisisAndTachycardiaAndFever_fire() {
        Map<String, Object> m = Map.of(
                "vitals", Map.of("systolicBp", 190, "heartRate", 130, "temperatureC", 39.0)
        );
        var ctx = ClinicalEvaluationContext.fromMap(m);
        assertThat(engine.evaluate(ctx).stream().map(RuleAlert::code))
                .contains("VITALS_HYPERTENSIVE_CRISIS", "VITALS_TACHYCARDIA", "VITALS_FEVER");
    }

    @Test
    void vitals_normalValues_doNotFire() {
        Map<String, Object> m = Map.of(
                "vitals", Map.of("systolicBp", 120, "diastolicBp", 78, "heartRate", 72, "temperatureC", 36.8)
        );
        var ctx = ClinicalEvaluationContext.fromMap(m);
        assertThat(engine.evaluate(ctx).stream().map(RuleAlert::code))
                .doesNotContain("VITALS_HYPERTENSIVE_CRISIS", "VITALS_TACHYCARDIA", "VITALS_BRADYCARDIA", "VITALS_FEVER");
    }

    @Test
    void vitals_absent_noFalsePositive() {
        var ctx = ClinicalEvaluationContext.fromMap(Map.of("diagnoses", List.of("ASTHMA")));
        assertThat(engine.evaluate(ctx).stream().map(RuleAlert::code))
                .noneMatch(c -> c.startsWith("VITALS_"));
    }

    // ── Asthma + beta-blocker contraindication & monitoring ──────────────────

    @Test
    void asthmaBetaBlocker_firesContraindication() {
        Map<String, Object> m = Map.of(
                "diagnoses", List.of("ASTHMA"),
                "activeMedications", List.of(Map.of("genericName", "propranolol", "displayName", "Propranolol"))
        );
        var ctx = ClinicalEvaluationContext.fromMap(m);
        assertThat(engine.evaluate(ctx).stream().map(RuleAlert::code))
                .contains("ASTHMA_BETA_BLOCKER_CONTRAINDICATION");
    }

    @Test
    void monitoring_diabetesAndHypertension_fireInformational() {
        Map<String, Object> m = Map.of("diagnoses", List.of("TYPE 2 DIABETES E11", "ESSENTIAL HYPERTENSION I10"));
        var ctx = ClinicalEvaluationContext.fromMap(m);
        assertThat(engine.evaluate(ctx).stream().map(RuleAlert::code))
                .contains("MONITORING_DIABETES", "MONITORING_HYPERTENSION");
    }
}
