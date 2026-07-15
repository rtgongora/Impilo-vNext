package zw.gov.mohcc.impilo.sharedkernel.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Shared emergency-access (break-glass) guard, modelled on PCT's {@code ClinicalAccessGuard}.
 * Framework-agnostic: a service passes the purpose-of-use that Envoy ext_authz / TSHEPO already
 * stamped into its trust context, and the guard decides whether a high-risk emergency action (e.g.
 * MADI O-negative / uncrossmatched blood release) is a governed BREAK_GLASS override or must be
 * denied.
 *
 * <p>An emergency override is NEVER a silent bypass: it is only granted under an explicit
 * {@code EMERGENCY} / {@code BREAK_GLASS} purpose-of-use, and it emits an elevated-visibility audit
 * line here — while the authoritative decision record stays upstream in the ext_authz/TSHEPO ledger
 * (this guard does not, and must not, mint its own authz). Actions NOT flagged emergency require the
 * emergency purpose and are otherwise refused.</p>
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
