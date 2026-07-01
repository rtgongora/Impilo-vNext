package zw.gov.mohcc.impilo.pct.core.forms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.core.cadre.CadreDecision;
import zw.gov.mohcc.impilo.pct.core.cadre.CadreDecisionRequest;
import zw.gov.mohcc.impilo.pct.core.cadre.CadreEngine;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure GAP-4 unification tests: form scope = cadre permitted-workflows ∩ form required-workflow,
 * with escalation → countersign. Uses the REAL {@link CadreEngine} so the two engines are verified
 * together without coupling.
 */
class FormScopeEngineTest {

    private static FormCatalogEntry form(String key, String workflow, String obligation, String setting) {
        return new FormCatalogEntry(
                "schema-" + key, key, key, null, 1, "ver-" + key, "PUBLISHED", "TEST",
                setting == null ? List.of() : List.of(setting), List.of(), List.of(), List.of(), List.of(),
                workflow, obligation, List.of(), null, null, "ALL", null, List.of(), List.of(),
                false, true, "STANDARD", "{}", null);
    }

    private static CadreDecision cadre(String cadre, String context, Integer acuity) {
        return CadreEngine.resolve(new CadreDecisionRequest("PROVIDER", cadre, "FACILITY", "GENERAL",
                acuity, context, "OK"));
    }

    private static FormResolution resolve(CadreDecision d, String setting, PatientFacts patient, FormCatalogEntry... forms) {
        return FormScopeEngine.resolve(new FormResolutionRequest(
                d, setting, "ASSESSMENT", "GENERAL", "outpatient", 3, patient, "TEST_CADRE", List.of(forms)));
    }

    private static boolean has(List<FormResolution.FormObligation> list, String key) {
        return list.stream().anyMatch(o -> o.formKey().equals(key));
    }

    @Test
    @DisplayName("Nurse: TRIAGE mandatory, PRESCRIBE countersign-required, DIAGNOSE-only-form countersign via supervisor")
    void nurseGates() {
        CadreDecision nurse = cadre("NURSE", "OUTPATIENT", 3);
        FormResolution r = resolve(nurse, "OUTPATIENT", PatientFacts.unknown(),
                form("triage", "TRIAGE", "MANDATORY", "OUTPATIENT"),
                form("prescribe", "PRESCRIBE", "OPTIONAL", "OUTPATIENT"));

        assertThat(has(r.mandatory(), "triage")).isTrue();
        // Nurse cannot independently PRESCRIBE but escalation lists it → countersign, NOT prohibited.
        assertThat(has(r.countersignRequired(), "prescribe")).isTrue();
        assertThat(has(r.prohibited(), "prescribe")).isFalse();
    }

    @Test
    @DisplayName("Physician: DIAGNOSE + PRESCRIBE forms are directly completable")
    void physicianGates() {
        CadreDecision doc = cadre("DOCTOR", "OUTPATIENT", 3);
        FormResolution r = resolve(doc, "OUTPATIENT", PatientFacts.unknown(),
                form("consult", "DIAGNOSE", "MANDATORY", "OUTPATIENT"),
                form("rx", "PRESCRIBE", "RECOMMENDED", "OUTPATIENT"));

        assertThat(has(r.mandatory(), "consult")).isTrue();
        assertThat(has(r.recommended(), "rx")).isTrue();
        assertThat(r.prohibited()).isEmpty();
        assertThat(r.countersignRequired()).isEmpty();
    }

    @Test
    @DisplayName("Pharmacy: a DIAGNOSE form is prohibited (neither permitted nor supervisor-eligible)")
    void pharmacyProhibited() {
        CadreDecision pharm = cadre("PHARMACIST", "OUTPATIENT", 3);
        FormResolution r = resolve(pharm, "OUTPATIENT", PatientFacts.unknown(),
                form("dx", "DIAGNOSE", "OPTIONAL", "OUTPATIENT"),
                form("medrev", "MEDICATION_REVIEW", "RECOMMENDED", "OUTPATIENT"));

        assertThat(has(r.prohibited(), "dx")).isTrue();
        assertThat(has(r.recommended(), "medrev")).isTrue();
        FormResolution.FormObligation dx = r.prohibited().stream()
                .filter(o -> o.formKey().equals("dx")).findFirst().orElseThrow();
        assertThat(dx.reason()).contains("not permitted");
    }

    @Test
    @DisplayName("Emergency (acuity 1) widens the spine: emergency ORDER_LAB form becomes available to a nurse")
    void emergencyWidening() {
        CadreDecision emergencyNurse = cadre("NURSE", "CASUALTY", 1);
        FormResolution r = resolve(emergencyNurse, "EMERGENCY", PatientFacts.unknown(),
                form("emlab", "ORDER_LAB", "MANDATORY", "EMERGENCY"));
        assertThat(has(r.prohibited(), "emlab")).isFalse();
        assertThat(has(r.mandatory(), "emlab")).isTrue();
    }

    @Test
    @DisplayName("Patient applicability: pregnancy-only and age-bounded forms are dropped when non-applicable")
    void patientApplicability() {
        CadreDecision nurse = cadre("NURSE", "OUTPATIENT", 3);
        FormCatalogEntry anc = new FormCatalogEntry("s-anc", "anc", "ANC", null, 1, "v-anc", "PUBLISHED", "PROG",
                List.of("OUTPATIENT"), List.of(), List.of(), List.of(), List.of(),
                "ASSESS", "MANDATORY", List.of(), 120, null, "FEMALE", Boolean.TRUE, List.of(), List.of(),
                false, true, "SENSITIVE", "{}", null);
        FormCatalogEntry imci = new FormCatalogEntry("s-imci", "imci", "IMCI", null, 1, "v-imci", "PUBLISHED", "PROG",
                List.of("OUTPATIENT"), List.of(), List.of(), List.of(), List.of(),
                "ASSESS", "MANDATORY", List.of(), 0, 60, "ALL", null, List.of(), List.of(),
                false, true, "STANDARD", "{}", null);

        // Adult male, not pregnant: ANC dropped (sex+pregnancy), IMCI dropped (age > 60mo).
        PatientFacts adultMale = new PatientFacts(360, "MALE", false, List.of(), List.of());
        FormResolution rMale = resolve(nurse, "OUTPATIENT", adultMale, anc, imci);
        assertThat(has(rMale.mandatory(), "anc")).isFalse();
        assertThat(has(rMale.mandatory(), "imci")).isFalse();

        // Pregnant adult female: ANC applies.
        PatientFacts pregnantFemale = new PatientFacts(300, "FEMALE", true, List.of(), List.of());
        FormResolution rAnc = resolve(nurse, "OUTPATIENT", pregnantFemale, anc, imci);
        assertThat(has(rAnc.mandatory(), "anc")).isTrue();

        // Infant (12 months): IMCI applies.
        PatientFacts infant = new PatientFacts(12, "FEMALE", null, List.of(), List.of());
        FormResolution rImci = resolve(nurse, "OUTPATIENT", infant, anc, imci);
        assertThat(has(rImci.mandatory(), "imci")).isTrue();
    }

    @Test
    @DisplayName("Setting filter: an INPATIENT-only form is dropped in an OUTPATIENT encounter")
    void settingFilter() {
        CadreDecision doc = cadre("DOCTOR", "OUTPATIENT", 3);
        FormResolution r = resolve(doc, "OUTPATIENT", PatientFacts.unknown(),
                form("admit", "ADMIT", "MANDATORY", "INPATIENT"));
        assertThat(has(r.mandatory(), "admit")).isFalse();
        assertThat(has(r.prohibited(), "admit")).isFalse(); // dropped, not prohibited
    }

    @Test
    @DisplayName("Determinism: identical inputs produce identical resolutions")
    void deterministic() {
        CadreDecision nurse = cadre("NURSE", "OUTPATIENT", 3);
        FormCatalogEntry[] forms = {
                form("a", "TRIAGE", "MANDATORY", "OUTPATIENT"),
                form("b", "PRESCRIBE", "OPTIONAL", "OUTPATIENT")};
        FormResolution r1 = resolve(nurse, "OUTPATIENT", PatientFacts.unknown(), forms);
        FormResolution r2 = resolve(nurse, "OUTPATIENT", PatientFacts.unknown(), forms);
        assertThat(r1.mandatory().stream().map(FormResolution.FormObligation::formKey).toList())
                .isEqualTo(r2.mandatory().stream().map(FormResolution.FormObligation::formKey).toList());
        assertThat(r1.countersignRequired().stream().map(FormResolution.FormObligation::formKey).toList())
                .isEqualTo(r2.countersignRequired().stream().map(FormResolution.FormObligation::formKey).toList());
    }
}
