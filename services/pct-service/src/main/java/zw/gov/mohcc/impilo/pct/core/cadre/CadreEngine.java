package zw.gov.mohcc.impilo.pct.core.cadre;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The PCT Cadre Engine (shared read-model C9) — the adaptive-spine resolver for the Encounter Cockpit.
 *
 * <p>This is <strong>real workflow enforcement</strong>, distinct from Tshepo authorization. Tshepo answers
 * "<em>may this actor perform this action?</em>" (RBAC/ABAC). The Cadre Engine answers "<em>given role +
 * cadre + scope + visit-type + acuity + work-context + access-state, what workflow is this person permitted
 * to drive right now, and which Encounter Cockpit tabs/actions light up?</em>".</p>
 *
 * <p>It is <strong>pure and deterministic</strong>: same inputs → same decision. It holds no state and
 * performs no I/O. Auditing of each resolution is the caller's responsibility ({@code CadreEngineService}),
 * which also stamps the {@code auditRef}.</p>
 *
 * <p><strong>Doctrine — no fake completions:</strong> a disabled action is still emitted in the spine (so the
 * cockpit can grey it out with a reason) but the cockpit must never render a disabled action as a live button.
 * Authz-gated actions still call the existing Tshepo ext_authz path at execution; this engine never authors
 * policy. Where a downstream action requires a Tshepo rule, see the policy contract IDs noted inline
 * (TODO references route to track P / WS-OPA {@code impilo.authz}).</p>
 */
public final class CadreEngine {

    // ---- Cadre families (coarse capability tiers; mapped from Varapi cadre/profession) ----
    private static final Set<String> PHYSICIAN_CADRES = Set.of(
            "MEDICAL_PRACTITIONER", "MEDICAL_OFFICER", "SPECIALIST", "CONSULTANT",
            "REGISTRAR", "RESIDENT", "PHYSICIAN", "DOCTOR");
    private static final Set<String> CLINICAL_OFFICER_CADRES = Set.of(
            "CLINICAL_OFFICER", "CLINICAL_ASSOCIATE", "PHYSICIAN_ASSISTANT", "MEDICAL_LICENTIATE");
    private static final Set<String> NURSE_CADRES = Set.of(
            "NURSE", "REGISTERED_NURSE", "MIDWIFE", "NURSE_PRACTITIONER", "PRIMARY_CARE_NURSE",
            "STATE_CERTIFIED_NURSE", "STATE_REGISTERED_NURSE");
    private static final Set<String> PHARMACY_CADRES = Set.of(
            "PHARMACIST", "PHARMACY_TECHNICIAN", "DISPENSARY_ASSISTANT");
    private static final Set<String> COMMUNITY_CADRES = Set.of(
            "COMMUNITY_HEALTH_WORKER", "VILLAGE_HEALTH_WORKER", "CHW", "VHW", "OUTREACH_WORKER");

    private CadreEngine() {}

    /**
     * Resolve the permitted-workflow set, adaptive cockpit spine, and escalation hints for a request.
     * Pure: deterministic and side-effect-free. {@code auditRef} is left {@code null} for the caller to stamp.
     */
    public static CadreDecision resolve(CadreDecisionRequest req) {
        CadreFamily family = familyOf(req.cadreOrUnknown(), req.roleOrUnknown());
        String context = req.contextOrOutpatient();
        String accessState = req.accessStateOrUnknown();
        int acuity = req.acuityClamped();
        boolean emergency = isEmergency(context, accessState, acuity);

        Set<String> workflows = permittedWorkflows(family, context, emergency);
        List<CadreDecision.CockpitTab> spine = cockpitSpine(family, context, accessState, acuity, emergency, workflows);
        CadreDecision.Escalation escalation = escalation(family, context, acuity, emergency);

        return new CadreDecision(List.copyOf(workflows), spine, escalation, null);
    }

    // ------------------------------------------------------------------
    // Cadre family classification
    // ------------------------------------------------------------------

    enum CadreFamily { PHYSICIAN, CLINICAL_OFFICER, NURSE, PHARMACY, COMMUNITY, OTHER }

    static CadreFamily familyOf(String cadre, String role) {
        if (PHYSICIAN_CADRES.contains(cadre)) return CadreFamily.PHYSICIAN;
        if (CLINICAL_OFFICER_CADRES.contains(cadre)) return CadreFamily.CLINICAL_OFFICER;
        if (NURSE_CADRES.contains(cadre)) return CadreFamily.NURSE;
        if (PHARMACY_CADRES.contains(cadre)) return CadreFamily.PHARMACY;
        if (COMMUNITY_CADRES.contains(cadre)) return CadreFamily.COMMUNITY;
        // Fall back to role hints when cadre is unmapped.
        if (role.contains("DOCTOR") || role.contains("PHYSICIAN")) return CadreFamily.PHYSICIAN;
        if (role.contains("NURSE") || role.contains("MIDWIFE")) return CadreFamily.NURSE;
        if (role.contains("PHARM")) return CadreFamily.PHARMACY;
        if (role.contains("CHW") || role.contains("COMMUNITY")) return CadreFamily.COMMUNITY;
        return CadreFamily.OTHER;
    }

    /** Law 1: acuity-1 always emergency; COSTA EMERGENCY_OVERRIDE access-state forces emergency mode. */
    static boolean isEmergency(String context, String accessState, int acuity) {
        return acuity <= 1
                || "EMERGENCY_OVERRIDE".equals(accessState)
                || "CASUALTY".equals(context);
    }

    // ------------------------------------------------------------------
    // Permitted workflows
    // ------------------------------------------------------------------

    static Set<String> permittedWorkflows(CadreFamily family, String context, boolean emergency) {
        Set<String> wf = new LinkedHashSet<>();
        // Everyone who can open a cockpit can assess and note.
        wf.add("ASSESS");
        wf.add("NOTE");

        switch (family) {
            case PHYSICIAN -> {
                wf.add("DIAGNOSE");
                wf.add("ORDER_LAB");
                wf.add("ORDER_IMAGING");
                wf.add("PRESCRIBE");
                wf.add("CARE_PLAN");
                wf.add("PROBLEMS");
                wf.add("REFER");
                wf.add("CONSULT");
                wf.add("ADMIT");
                wf.add("DISCHARGE");
                wf.add("SET_OUTCOME");
            }
            case CLINICAL_OFFICER -> {
                wf.add("DIAGNOSE");
                wf.add("ORDER_LAB");
                wf.add("ORDER_IMAGING");
                wf.add("PRESCRIBE");
                wf.add("CARE_PLAN");
                wf.add("PROBLEMS");
                wf.add("REFER");
                wf.add("CONSULT");
                // Clinical officers refer for admission; admission decision needs supervisor (escalation).
                wf.add("DISCHARGE");
                wf.add("SET_OUTCOME");
            }
            case NURSE -> {
                wf.add("TRIAGE");
                wf.add("ORDER_LAB");           // standing-order labs (protocol-bound)
                wf.add("CARE_PLAN");
                wf.add("PROBLEMS");
                wf.add("REFER");
                wf.add("SET_OUTCOME");
                // No independent PRESCRIBE / ADMIT for general nurses (supervisor / protocol gated).
            }
            case PHARMACY -> {
                wf.add("MEDICATION_REVIEW");
                wf.add("DISPENSE");
            }
            case COMMUNITY -> {
                wf.add("SCREEN");
                wf.add("REFER");
                wf.add("SET_OUTCOME");
            }
            case OTHER -> {
                // Minimal: assess + note only (read-mostly).
            }
        }

        if (emergency) {
            // Law 1: emergency widens the spine — life-saving actions are available to whoever is present,
            // gated downstream by break-glass + Tshepo (policy BREAK-GLASS / EMERGENCY-CARE-NEVER-BLOCKED).
            wf.add("TRIAGE");
            wf.add("ORDER_LAB");
            wf.add("ORDER_IMAGING");
            wf.add("SET_OUTCOME");
        }
        return wf;
    }

    // ------------------------------------------------------------------
    // Adaptive cockpit spine
    // ------------------------------------------------------------------

    static List<CadreDecision.CockpitTab> cockpitSpine(CadreFamily family, String context, String accessState,
                                                       int acuity, boolean emergency, Set<String> wf) {
        List<CadreDecision.CockpitTab> tabs = new ArrayList<>();

        // OVERVIEW — always present, always enabled.
        tabs.add(tab("OVERVIEW", true, List.of(
                action("VIEW_TIMELINE", true, false),
                action("VIEW_SUMMARY", true, false))));

        // ASSESSMENT — enabled whenever ASSESS permitted.
        tabs.add(tab("ASSESSMENT", wf.contains("ASSESS"), List.of(
                action("RECORD_VITALS", wf.contains("TRIAGE") || wf.contains("ASSESS"), false),
                action("RECORD_TRIAGE", wf.contains("TRIAGE"), false),
                // Diagnosis writes are step-up-gated when access is blocked pending payment (non-emergency).
                action("RECORD_DIAGNOSIS", wf.contains("DIAGNOSE"), stepUpForAccess(accessState, emergency)))));

        // PROBLEMS — problems-list CRUD.
        tabs.add(tab("PROBLEMS", wf.contains("PROBLEMS"), List.of(
                action("ADD_PROBLEM", wf.contains("PROBLEMS"), false),
                action("RESOLVE_PROBLEM", wf.contains("PROBLEMS"), false))));

        // ORDERS_RESULTS — labs/imaging/prescribe. Orders execute into OROS (consume; never edit OROS).
        tabs.add(tab("ORDERS_RESULTS", wf.contains("ORDER_LAB") || wf.contains("ORDER_IMAGING") || wf.contains("PRESCRIBE"), List.of(
                // policy ORDER-CREATE gates the OROS write at execution.
                action("ORDER_LAB", wf.contains("ORDER_LAB"), false),
                action("ORDER_IMAGING", wf.contains("ORDER_IMAGING"), false),
                // Prescribing is MAXIMUM friction — always step-up.
                action("PRESCRIBE", wf.contains("PRESCRIBE"), true),
                action("VIEW_RESULTS", true, false))));

        // CARE — care plan (outpatient parity with inpatient).
        tabs.add(tab("CARE", wf.contains("CARE_PLAN"), List.of(
                action("CREATE_CARE_PLAN", wf.contains("CARE_PLAN"), false),
                action("UPDATE_CARE_PLAN", wf.contains("CARE_PLAN"), false))));

        // CONSULTS_REFERRALS — referral + teleconsult.
        tabs.add(tab("CONSULTS_REFERRALS", wf.contains("REFER") || wf.contains("CONSULT"), List.of(
                action("CREATE_REFERRAL", wf.contains("REFER"), false),
                action("REQUEST_CONSULT", wf.contains("CONSULT"), false),
                action("START_TELECONSULT", wf.contains("CONSULT") || wf.contains("REFER"), false))));

        // NOTES — clinical notes.
        tabs.add(tab("NOTES", wf.contains("NOTE"), List.of(
                action("ADD_NOTE", wf.contains("NOTE"), false))));

        // VISIT_OUTCOME — disposition. Admit/Discharge are step-up; admission for non-physician escalates.
        List<CadreDecision.CockpitAction> outcomeActions = new ArrayList<>();
        outcomeActions.add(action("SET_OUTCOME", wf.contains("SET_OUTCOME"), false));
        outcomeActions.add(action("REQUEST_ADMISSION", wf.contains("ADMIT") || isClinical(family), true));
        outcomeActions.add(action("DISCHARGE", wf.contains("DISCHARGE"), true));
        tabs.add(tab("VISIT_OUTCOME", wf.contains("SET_OUTCOME") || wf.contains("DISCHARGE") || wf.contains("ADMIT"),
                outcomeActions));

        return tabs;
    }

    private static boolean stepUpForAccess(String accessState, boolean emergency) {
        if (emergency) {
            return false; // Law 1: never add friction to emergency care.
        }
        return "BLOCKED_PENDING_PAYMENT".equals(accessState)
                || "BLOCKED_PENDING_AUTHORISATION".equals(accessState)
                || "PAYMENT_REQUIRED_BEFORE_SERVICE".equals(accessState);
    }

    private static boolean isClinical(CadreFamily f) {
        return f == CadreFamily.PHYSICIAN || f == CadreFamily.CLINICAL_OFFICER || f == CadreFamily.NURSE;
    }

    // ------------------------------------------------------------------
    // Escalation
    // ------------------------------------------------------------------

    static CadreDecision.Escalation escalation(CadreFamily family, String context, int acuity, boolean emergency) {
        List<String> supervisorRequiredFor = new ArrayList<>();
        switch (family) {
            case NURSE -> {
                supervisorRequiredFor.add("PRESCRIBE");
                supervisorRequiredFor.add("ADMIT");
                supervisorRequiredFor.add("DISCHARGE");
            }
            case CLINICAL_OFFICER -> supervisorRequiredFor.add("ADMIT");
            case COMMUNITY -> {
                supervisorRequiredFor.add("PRESCRIBE");
                supervisorRequiredFor.add("DIAGNOSE");
            }
            case OTHER -> {
                supervisorRequiredFor.add("DIAGNOSE");
                supervisorRequiredFor.add("PRESCRIBE");
            }
            default -> { /* physicians / pharmacy: no blanket supervisor gate */ }
        }
        // Break-glass available in emergency or high-acuity presentations, audited at execution (policy BREAK-GLASS).
        boolean breakGlass = emergency || acuity <= 2;
        return new CadreDecision.Escalation(breakGlass, List.copyOf(supervisorRequiredFor));
    }

    // ------------------------------------------------------------------
    // Small builders
    // ------------------------------------------------------------------

    private static CadreDecision.CockpitTab tab(String name, boolean enabled, List<CadreDecision.CockpitAction> actions) {
        return new CadreDecision.CockpitTab(name, enabled, actions);
    }

    private static CadreDecision.CockpitAction action(String name, boolean enabled, boolean requiresStepUp) {
        return new CadreDecision.CockpitAction(name, enabled, requiresStepUp);
    }
}
