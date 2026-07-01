package zw.gov.mohcc.impilo.pct.core.forms;

import zw.gov.mohcc.impilo.pct.core.cadre.CadreDecision;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The pure Form Scope Engine — the adaptive resolver for encounter structured forms.
 *
 * <p>It is <strong>pure and deterministic</strong> (same inputs → same output, no I/O, no state) and
 * <strong>composes</strong> the {@link CadreDecision} produced by {@code CadreEngine} rather than
 * re-deriving cadre logic. This discharges registry GAP-4 ("unify cadre workflows with form scope rules")
 * by intersecting the two: <em>form scope = cadre permitted-workflows ∩ form catalog required-workflow</em>,
 * with cadre escalation → countersign.</p>
 *
 * <p><strong>Doctrine — no fake completions:</strong> a prohibited form is still emitted (with a reason) so
 * the cockpit can grey it out; it is never silently dropped for cadre reasons. Forms the patient is not
 * eligible for (age / sex / pregnancy / programme / setting) are dropped entirely.</p>
 */
public final class FormScopeEngine {

    private FormScopeEngine() {}

    public static FormResolution resolve(FormResolutionRequest req) {
        List<FormResolution.FormObligation> mandatory = new ArrayList<>();
        List<FormResolution.FormObligation> recommended = new ArrayList<>();
        List<FormResolution.FormObligation> optional = new ArrayList<>();
        List<FormResolution.FormObligation> prohibited = new ArrayList<>();
        List<FormResolution.FormObligation> countersign = new ArrayList<>();

        List<String> permitted = req.cadreDecision() == null ? List.of()
                : nn(req.cadreDecision().permittedWorkflows());
        List<String> supervisorRequired = req.cadreDecision() == null || req.cadreDecision().escalation() == null
                ? List.of() : nn(req.cadreDecision().escalation().supervisorRequiredFor());

        for (FormCatalogEntry entry : nn(req.catalog())) {
            if (entry == null) {
                continue;
            }
            // 1) Patient + setting applicability — non-applicable forms are dropped entirely.
            if (!patientApplicable(entry, req.patient()) || !settingApplicable(entry, req.careSetting())) {
                continue;
            }

            // 2) Cadre-workflow gate (GAP-4 unification):
            //    form scope        = cadre permitted-workflows ∩ form required-workflow.
            //    "may complete"    = directly permitted OR the CadreEngine lists it as escalation-eligible
            //                        (e.g. a Zimbabwe nurse prescribing — competence/programme/setting-based,
            //                        NOT a universal cadre-by-medication ban we can hard-code today).
            //    countersignature  = AUTHOR/POLICY-driven only (form.requiresCountersign, or a future
            //                        prescribing policy rule). Cadre escalation is surfaced as an advisory
            //                        supervisorRecommended hint, never a blanket hard gate. See the design
            //                        note on prescribing.
            String workflow = entry.requiredWorkflow();
            boolean workflowGated = workflow != null && !workflow.isBlank();
            boolean directlyPermitted = !workflowGated || containsIgnoreCase(permitted, workflow);
            boolean supervisorEligible = workflowGated && containsIgnoreCase(supervisorRequired, workflow);

            if (!directlyPermitted && !supervisorEligible) {
                // Neither independently permitted nor escalation-eligible → prohibited (with reason).
                prohibited.add(obligation(entry, "PROHIBITED", false,
                        "Cadre " + safe(req.providerCadre()) + " is not permitted to perform workflow "
                                + workflow + " for this form."));
            } else {
                boolean supervisorRecommended = supervisorEligible && !directlyPermitted;
                String advisory = supervisorRecommended
                        ? "Supervisor review recommended for " + workflow + " by this cadre" : null;
                if (entry.requiresCountersign()) {
                    // Author (or future policy) has flagged this specific form/action as needing countersign.
                    countersign.add(obligation(entry, "COUNTERSIGN_REQUIRED", supervisorRecommended, advisory));
                } else {
                    switch (upper(entry.obligationDefault())) {
                        case "MANDATORY" -> mandatory.add(obligation(entry, "MANDATORY", supervisorRecommended, advisory));
                        case "RECOMMENDED" -> recommended.add(obligation(entry, "RECOMMENDED", supervisorRecommended, advisory));
                        default -> optional.add(obligation(entry, "OPTIONAL", supervisorRecommended, advisory));
                    }
                }
            }
        }

        return new FormResolution(
                List.copyOf(mandatory), List.copyOf(recommended), List.copyOf(optional),
                List.copyOf(prohibited), List.copyOf(countersign), null);
    }

    // ------------------------------------------------------------------
    // Applicability
    // ------------------------------------------------------------------

    static boolean patientApplicable(FormCatalogEntry entry, PatientFacts patient) {
        PatientFacts p = patient == null ? PatientFacts.unknown() : patient;

        // Age band (only when both bounds and patient age are known).
        if (p.ageMonths() != null) {
            if (entry.minAgeMonths() != null && p.ageMonths() < entry.minAgeMonths()) {
                return false;
            }
            if (entry.maxAgeMonths() != null && p.ageMonths() > entry.maxAgeMonths()) {
                return false;
            }
        }

        // Sex (only filter when both known and specific).
        String sexApp = upper(entry.sexApplicability());
        if (!sexApp.isEmpty() && !"ALL".equals(sexApp)) {
            String sex = upper(p.sex());
            if (!sex.isEmpty() && !"UNKNOWN".equals(sex) && !sexApp.equals(sex)) {
                return false;
            }
        }

        // Pregnancy.
        if (Boolean.TRUE.equals(entry.requirePregnant()) && !Boolean.TRUE.equals(p.pregnant())) {
            return false;
        }
        if (Boolean.FALSE.equals(entry.requirePregnant()) && Boolean.TRUE.equals(p.pregnant())) {
            return false;
        }

        // Programme applicability: if the form is programme-scoped, require at least one match.
        List<String> progs = entry.programmesOrEmpty();
        if (!progs.isEmpty()) {
            boolean match = p.programmesOrEmpty().stream().anyMatch(pp -> containsIgnoreCase(progs, pp));
            if (!match) {
                return false;
            }
        }
        return true;
    }

    static boolean settingApplicable(FormCatalogEntry entry, String careSetting) {
        List<String> settings = entry.careSettingsOrEmpty();
        if (settings.isEmpty() || careSetting == null || careSetting.isBlank()) {
            return true; // form is setting-agnostic, or the caller did not scope by setting
        }
        return containsIgnoreCase(settings, careSetting);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static FormResolution.FormObligation obligation(FormCatalogEntry e, String verdict,
                                                            boolean supervisorRecommended, String reason) {
        return new FormResolution.FormObligation(
                e.formKey(), e.formSchemaId(), e.version(), e.formSchemaVersionId(),
                e.name(), verdict, e.requiredWorkflow(), e.requiresCountersign(), supervisorRecommended, reason);
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        if (list == null || value == null) {
            return false;
        }
        for (String s : list) {
            if (s != null && s.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static <T> List<T> nn(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String upper(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String safe(String s) {
        return s == null ? "UNKNOWN" : s;
    }
}
