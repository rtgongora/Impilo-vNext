package zw.gov.mohcc.impilo.sharedkernel.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Shared emergency-access (break-glass) guard, modelled on PCT's {@code ClinicalAccessGuard}.
 * Framework-agnostic: a service passes a purpose-of-use, and the guard decides whether a high-risk
 * emergency action (e.g. MADI O-negative / uncrossmatched blood release) may proceed.
 *
 * <h2>What this guard actually enforces — read before relying on it</h2>
 * <p><b>This is a string comparison, not an authorization check.</b> It compares the supplied
 * purpose-of-use against {@code EMERGENCY} / {@code BREAK_GLASS} and nothing else. Any caller able
 * to set {@code X-Purpose-Of-Use: BREAK_GLASS} satisfies it, so on this path break-glass unlocks
 * emergency clinical access with no emergency, no approval and no grant.</p>
 *
 * <p>This javadoc previously said an override "is NEVER a silent bypass" and that "the authoritative
 * decision record stays upstream in the ext_authz/TSHEPO ledger". Both claims were measured during
 * Phase 0 workstream A and are false on this path:</p>
 *
 * <ul>
 *   <li>Envoy's configuration defines two clusters, both experience-bff, and routes to no domain
 *       service. madi-service — the only caller of this guard — is not behind ext_authz, so no
 *       upstream decision is made and none is recorded.</li>
 *   <li>The purpose-of-use it compares is therefore client-supplied on that path, not PDP-stamped.
 *       The comparison is made against a value the caller chose.</li>
 * </ul>
 *
 * <p>The {@code log.warn} below is real, and is the only artefact an override currently produces.</p>
 *
 * <h2>Why this was not wired to the real grant model</h2>
 * <p>A genuine grant model does exist — {@code BreakGlassRequestEntity},
 * {@code VisibilityEscalationGrantEntity}, {@code BreakGlassService} and
 * {@code VisibilityEscalationService} in tshepo-authz-service — and
 * {@code VisibilityEscalationService.resolveActiveGrant} validates a grant token properly, against
 * tenant, actor and expiry. Consulting it was the intended fix. It could not be done honestly, for
 * reasons worth recording rather than working around:</p>
 *
 * <ul>
 *   <li><b>There is no seam to wire to.</b> {@code resolveActiveGrant} has exactly one production
 *       caller — {@code PolicyEngine:280}, inside the PDP's own authorize path. tshepo-authz
 *       exposes {@code /v1/visibility-escalations/requests}, {@code /requests/pending},
 *       {@code /requests/{id}/review} and {@code /v1/break-glass}: the workflow for
 *       <em>obtaining</em> a grant. Nothing exposes <em>validating</em> one to a downstream
 *       service.</li>
 *   <li><b>{@code x-escalation-grant-id} is not consumed downstream today.</b> It is PDP-stamped and
 *       does appear in Envoy's {@code allowed_upstream_headers}, so it is trustworthy — but only on
 *       the Envoy path, which reaches experience-bff alone. Its two uses there are copying it into
 *       the P1 shadow-observation envelope (verdict-free by design, changes nothing) and echoing it
 *       into a {@code /visibility-profile} response body. Neither validates it. varapi's
 *       {@code OversightEscalationGrantEntity} is an unrelated varapi-local table for HPA oversight,
 *       not this header.</li>
 *   <li>So requiring the header here would check that a caller who can already forge the purpose can
 *       also supply a well-formed UUID. That reads like validation and is not — which is exactly the
 *       failure mode the correction above is about.</li>
 * </ul>
 *
 * <p>Closing this needs a grant-validation call tshepo-authz does not expose yet, and the decision to
 * fail closed on it is a patient-safety decision — the affected path is emergency blood release —
 * not a refactor. Both belong with whoever owns the trust plane. The exposure is left accurately
 * described rather than half-fixed.</p>
 */
public final class EmergencyAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(EmergencyAccessGuard.class);

    private EmergencyAccessGuard() {
    }

    /** Whether the caller is operating under an emergency / break-glass purpose of use. */
    public static boolean isEmergencyPurpose(String purposeOfUse) {
        if (purposeOfUse == null) return false;
        String p = purposeOfUse.trim().toUpperCase();
        return p.equals("EMERGENCY") || p.equals("BREAK_GLASS");
    }

    /**
     * Authorise a high-risk emergency action (e.g. uncrossmatched / O-neg blood release). Permitted
     * ONLY under an emergency/break-glass purpose; the grant is audited as a break-glass event.
     *
     * @throws EmergencyAccessDeniedException when the purpose is not emergency/break-glass.
     * @return an audited {@link Decision} (always {@code breakGlass=true} on success).
     */
    public static Decision requireBreakGlass(String purposeOfUse, String action, String subjectRef, String actorId) {
        if (!isEmergencyPurpose(purposeOfUse)) {
            throw new EmergencyAccessDeniedException(
                    "Emergency action '" + action + "' requires EMERGENCY/BREAK_GLASS purpose-of-use (got: "
                            + (purposeOfUse == null ? "none" : purposeOfUse) + ")");
        }
        log.warn("BREAK-GLASS EMERGENCY ACCESS: action={} subject={} actor={} purpose={} — governed override, "
                        + "elevated audit; authoritative decision recorded upstream (ext_authz/TSHEPO).",
                action, subjectRef, actorId, purposeOfUse);
        return new Decision(true, "BREAK_GLASS", Objects.requireNonNullElse(purposeOfUse, "EMERGENCY"), action, subjectRef);
    }

    /** The outcome of an emergency-access evaluation (all fields non-secret; safe to persist/audit). */
    public record Decision(boolean breakGlass, String mode, String purposeOfUse, String action, String subjectRef) {}

    /** Thrown when an emergency action is attempted without an emergency/break-glass purpose. */
    public static class EmergencyAccessDeniedException extends RuntimeException {
        public EmergencyAccessDeniedException(String message) {
            super(message);
        }
    }
}
