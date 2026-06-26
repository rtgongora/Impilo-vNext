package zw.gov.mohcc.impilo.pct.core.cadre;

import java.util.List;

/**
 * Output of the {@link CadreEngine} (shared read-model C9).
 *
 * <p>The Cadre Engine answers "<em>given role + cadre + scope + visit-type + acuity +
 * work-context + access-state, what workflow is this person permitted to drive right now,
 * and which Encounter Cockpit tabs/actions light up?</em>". It is real workflow enforcement,
 * distinct from Tshepo authorization (RBAC/ABAC). The cockpit renders strictly from
 * {@link #cockpitSpine()}; a disabled action is never rendered as a dead button.</p>
 *
 * <p>Authz-gated actions still call the existing Tshepo ext_authz path at execution time —
 * this engine does not author policy. It only resolves the adaptive spine.</p>
 *
 * <p>The wire shape mirrors {@code CadreDecision} in
 * {@code docs/design/provider-clinical-place/shared-read-models.md} (§C9) field-for-field.</p>
 */
public record CadreDecision(
        List<String> permittedWorkflows,
        List<CockpitTab> cockpitSpine,
        Escalation escalation,
        String auditRef) {

    /** An adaptive cockpit tab with its enabled actions. */
    public record CockpitTab(
            String tab,
            boolean enabled,
            List<CockpitAction> actions) {}

    /** A single action within a cockpit tab. */
    public record CockpitAction(
            String action,
            boolean enabled,
            boolean requiresStepUp) {}

    /** Escalation hints: when step-up / supervisor / break-glass is required. */
    public record Escalation(
            boolean breakGlassAvailable,
            List<String> supervisorRequiredFor) {}
}
