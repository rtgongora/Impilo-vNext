package zw.gov.mohcc.impilo.pct.core.cadre;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CadreEngineTest {

    private CadreDecision resolve(String cadre, String role, String scope, String visitType,
                                  Integer acuity, String context, String accessState) {
        return CadreEngine.resolve(new CadreDecisionRequest(role, cadre, scope, visitType, acuity, context, accessState));
    }

    private CadreDecision.CockpitTab tab(CadreDecision d, String name) {
        return d.cockpitSpine().stream().filter(t -> t.tab().equals(name)).findFirst().orElseThrow();
    }

    private Optional<CadreDecision.CockpitAction> action(CadreDecision.CockpitTab t, String name) {
        return t.actions().stream().filter(a -> a.action().equals(name)).findFirst();
    }

    @Test
    void physician_outpatient_gets_full_clinical_spine() {
        CadreDecision d = resolve("MEDICAL_PRACTITIONER", "DOCTOR", "FACILITY", "OPD", 3, "OUTPATIENT", "ALLOWED_WITHOUT_PAYMENT");
        assertTrue(d.permittedWorkflows().contains("PRESCRIBE"));
        assertTrue(d.permittedWorkflows().contains("ADMIT"));
        assertTrue(d.permittedWorkflows().contains("DISCHARGE"));
        assertTrue(tab(d, "ORDERS_RESULTS").enabled());
        // Prescribing is always step-up (MAXIMUM friction).
        assertTrue(action(tab(d, "ORDERS_RESULTS"), "PRESCRIBE").orElseThrow().requiresStepUp());
        // Physician carries no blanket supervisor gate.
        assertTrue(d.escalation().supervisorRequiredFor().isEmpty());
    }

    @Test
    void nurse_cannot_independently_prescribe_or_admit() {
        CadreDecision d = resolve("REGISTERED_NURSE", "NURSE", "WARD", "OPD", 4, "OUTPATIENT", "ALLOWED_WITHOUT_PAYMENT");
        assertFalse(d.permittedWorkflows().contains("PRESCRIBE"));
        assertFalse(d.permittedWorkflows().contains("ADMIT"));
        assertTrue(d.permittedWorkflows().contains("TRIAGE"));
        assertTrue(d.escalation().supervisorRequiredFor().contains("PRESCRIBE"));
        assertTrue(d.escalation().supervisorRequiredFor().contains("ADMIT"));
        // Prescribe action is not enabled for the nurse.
        assertFalse(action(tab(d, "ORDERS_RESULTS"), "PRESCRIBE").orElseThrow().enabled());
    }

    @Test
    void clinical_officer_admit_requires_supervisor() {
        CadreDecision d = resolve("CLINICAL_OFFICER", "CO", "FACILITY", "OPD", 3, "OUTPATIENT", "ALLOWED_WITHOUT_PAYMENT");
        assertTrue(d.permittedWorkflows().contains("PRESCRIBE"));
        assertFalse(d.permittedWorkflows().contains("ADMIT"));
        assertTrue(d.escalation().supervisorRequiredFor().contains("ADMIT"));
    }

    @Test
    void emergency_override_widens_spine_and_enables_break_glass() {
        // Access blocked pending payment, but acuity-1 / emergency-override: Law 1 — care first.
        CadreDecision d = resolve("REGISTERED_NURSE", "NURSE", "FACILITY", "ED", 1, "CASUALTY", "EMERGENCY_OVERRIDE");
        assertTrue(d.escalation().breakGlassAvailable());
        assertTrue(d.permittedWorkflows().contains("ORDER_LAB"));
        assertTrue(d.permittedWorkflows().contains("ORDER_IMAGING"));
        // Diagnosis recording must NOT be step-up-gated in emergency (no friction on emergency care).
        CadreDecision.CockpitAction diag = action(tab(d, "ASSESSMENT"), "RECORD_DIAGNOSIS").orElseThrow();
        assertFalse(diag.requiresStepUp());
    }

    @Test
    void blocked_payment_non_emergency_adds_stepup_to_diagnosis() {
        CadreDecision d = resolve("MEDICAL_PRACTITIONER", "DOCTOR", "FACILITY", "OPD", 4, "OUTPATIENT", "BLOCKED_PENDING_PAYMENT");
        CadreDecision.CockpitAction diag = action(tab(d, "ASSESSMENT"), "RECORD_DIAGNOSIS").orElseThrow();
        assertTrue(diag.requiresStepUp());
    }

    @Test
    void deposit_authorisation_and_waiver_required_add_stepup_to_diagnosis() {
        // COSTA can return DEPOSIT_REQUIRED / AUTHORISATION_REQUIRED / WAIVER_REQUIRED as well as the
        // BLOCKED_* variants. None of these mean "service is payable yet", so a non-emergency clinical write
        // must be step-up-gated. Previously these three fell through and the write was let through ungated.
        for (String accessState : List.of("DEPOSIT_REQUIRED", "AUTHORISATION_REQUIRED", "WAIVER_REQUIRED")) {
            CadreDecision d = resolve("MEDICAL_PRACTITIONER", "DOCTOR", "FACILITY", "OPD", 4, "OUTPATIENT", accessState);
            CadreDecision.CockpitAction diag = action(tab(d, "ASSESSMENT"), "RECORD_DIAGNOSIS").orElseThrow();
            assertTrue(diag.requiresStepUp(),
                    "RECORD_DIAGNOSIS must be step-up-gated for access state " + accessState);
        }
    }

    @Test
    void emergency_never_stepup_gated_even_for_new_access_states() {
        // Law 1: emergency care is never gated, regardless of the COSTA access state.
        for (String accessState : List.of("DEPOSIT_REQUIRED", "AUTHORISATION_REQUIRED", "WAIVER_REQUIRED")) {
            CadreDecision d = resolve("MEDICAL_PRACTITIONER", "DOCTOR", "FACILITY", "ED", 1, "CASUALTY", accessState);
            CadreDecision.CockpitAction diag = action(tab(d, "ASSESSMENT"), "RECORD_DIAGNOSIS").orElseThrow();
            assertFalse(diag.requiresStepUp(),
                    "Emergency care must never be step-up-gated for access state " + accessState);
        }
    }

    @Test
    void community_worker_screens_and_refers_only() {
        CadreDecision d = resolve("COMMUNITY_HEALTH_WORKER", "CHW", "ABOVE_SITE", "OUTREACH", 5, "COMMUNITY", "EXEMPT");
        assertTrue(d.permittedWorkflows().contains("SCREEN"));
        assertTrue(d.permittedWorkflows().contains("REFER"));
        assertFalse(d.permittedWorkflows().contains("PRESCRIBE"));
        assertTrue(d.escalation().supervisorRequiredFor().contains("DIAGNOSE"));
    }

    @Test
    void overview_tab_always_present_and_enabled() {
        CadreDecision d = resolve("UNKNOWN_CADRE", "PORTER", "FACILITY", "GENERAL", 5, "OUTPATIENT", "ALLOWED_WITHOUT_PAYMENT");
        assertTrue(tab(d, "OVERVIEW").enabled());
        // OTHER family: minimal — no diagnose/prescribe.
        assertFalse(d.permittedWorkflows().contains("DIAGNOSE"));
        assertTrue(d.permittedWorkflows().contains("ASSESS"));
    }

    @Test
    void deterministic_same_inputs_same_decision() {
        CadreDecision a = resolve("MEDICAL_PRACTITIONER", "DOCTOR", "FACILITY", "OPD", 3, "OUTPATIENT", "ALLOWED_WITHOUT_PAYMENT");
        CadreDecision b = resolve("MEDICAL_PRACTITIONER", "DOCTOR", "FACILITY", "OPD", 3, "OUTPATIENT", "ALLOWED_WITHOUT_PAYMENT");
        assertEquals(a.permittedWorkflows(), b.permittedWorkflows());
        assertEquals(a.cockpitSpine(), b.cockpitSpine());
        assertEquals(a.escalation(), b.escalation());
    }

    @Test
    void null_inputs_default_safely() {
        CadreDecision d = CadreEngine.resolve(new CadreDecisionRequest(null, null, null, null, null, null, null));
        assertNotNull(d.permittedWorkflows());
        assertFalse(d.cockpitSpine().isEmpty());
        List<String> tabs = d.cockpitSpine().stream().map(CadreDecision.CockpitTab::tab).toList();
        assertTrue(tabs.contains("OVERVIEW"));
    }
}
