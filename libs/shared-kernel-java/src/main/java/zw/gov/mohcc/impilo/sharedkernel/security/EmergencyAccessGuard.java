package zw.gov.mohcc.impilo.sharedkernel.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Emergency-access (break-glass) guard for high-risk clinical actions — MHP activation, MHP pack
 * issue, and MADI O-negative / uncrossmatched blood release.
 *
 * <h2>What it enforces</h2>
 * <p>An emergency purpose-of-use is necessary and <b>not sufficient</b>. The caller must also hold a
 * real escalation grant, which is checked against the trust plane through
 * {@link EscalationGrantValidator}. Three outcomes, kept deliberately distinct:</p>
 *
 * <pre>
 *   VALID        → allow. A governed override.
 *   NO_GRANT     → REFUSE. Purpose alone is not authority.
 *   UNREACHABLE  → allow, and record an ungoverned override for post-hoc review.
 * </pre>
 *
 * <h2>Why UNREACHABLE allows (PO ruling, 2026-08-07)</h2>
 * <p>"Fail closed always" was considered and rejected, because the paths this guards include
 * uncrossmatched and O-negative blood release. A tshepo-authz outage is not a clinical fact, and
 * letting one block a transfusion trades a governance risk for a mortality risk. So the ruling
 * distinguishes the two: <em>no grant exists</em> is a refusal, <em>we could not ask</em> is an
 * allow that incurs a named review obligation.</p>
 *
 * <p><b>The distinction is the control.</b> If a transport failure is ever caught as "no grant" —
 * or worse, as "grant present" — this collapses back into what it replaced: a check that looks like
 * validation and is not. {@link EscalationGrantValidator} says the same thing at the seam where the
 * mistake would actually be made, and the guard's tests assert all three branches separately for the
 * same reason.</p>
 *
 * <h2>What this replaced</h2>
 * <p>Until now this was a string comparison. It compared the supplied purpose-of-use against
 * {@code EMERGENCY} / {@code BREAK_GLASS} and nothing else, so any caller able to set
 * {@code X-Purpose-Of-Use: BREAK_GLASS} unlocked emergency clinical access with no emergency, no
 * approval and no grant. Its javadoc claimed the override was "NEVER a silent bypass" and that the
 * decision was "recorded upstream in the ext_authz/TSHEPO ledger"; both were false on this path,
 * because Envoy routes only to experience-bff and madi-service is not behind ext_authz.</p>
 *
 * <p>The grant model existed the whole time — {@code VisibilityEscalationGrantEntity},
 * {@code VisibilityEscalationService} — but had no seam a domain service could reach:
 * {@code resolveActiveGrant} was called only from inside {@code PolicyEngine}, and the API offered
 * only the workflow for <em>obtaining</em> a grant. That seam now exists as
 * {@code POST /v1/visibility-escalations/grants/validate}, and this guard is its first caller.</p>
 *
 * <p>History and the original measurements are kept in
 * {@code docs/security/break-glass-guards-do-not-consult-the-grant.md}.</p>
 *
 * <h2>Still open</h2>
 * <p>{@code ClinicalAccessGuard} in pct-service has the identical original defect and is <b>not</b>
 * fixed by this change. See the doc above.</p>
 */
public final class EmergencyAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(EmergencyAccessGuard.class);

    private final EscalationGrantValidator grantValidator;
    private final UngovernedOverrideRecorder overrideRecorder;

    /**
     * Both collaborators are required. There is deliberately no constructor that omits the
     * recorder: the UNREACHABLE branch is only permissible <em>because</em> it is recorded, so a
     * guard that could allow without recording would not be implementing the ruling.
     */
    public EmergencyAccessGuard(EscalationGrantValidator grantValidator,
                                UngovernedOverrideRecorder overrideRecorder) {
        this.grantValidator = Objects.requireNonNull(grantValidator, "grantValidator");
        this.overrideRecorder = Objects.requireNonNull(overrideRecorder, "overrideRecorder");
    }

    /** Whether the caller is operating under an emergency / break-glass purpose of use. */
    public static boolean isEmergencyPurpose(String purposeOfUse) {
        if (purposeOfUse == null) return false;
        String p = purposeOfUse.trim().toUpperCase();
        return p.equals("EMERGENCY") || p.equals("BREAK_GLASS");
    }

    /**
     * Authorise a high-risk emergency action.
     *
     * @throws EmergencyAccessDeniedException when the purpose is not emergency/break-glass, or when
     *         the trust plane answered that no grant holds
     * @return an audited {@link Decision}; {@link Decision#governed()} is false when the trust plane
     *         could not be asked, in which case an ungoverned override has been recorded
     */
    public Decision requireBreakGlass(EmergencyAccessRequest request) {
        Objects.requireNonNull(request, "request");

        if (!isEmergencyPurpose(request.purposeOfUse())) {
            throw new EmergencyAccessDeniedException(
                    "Emergency action '" + request.action() + "' requires EMERGENCY/BREAK_GLASS "
                            + "purpose-of-use (got: "
                            + (request.purposeOfUse() == null ? "none" : request.purposeOfUse()) + ")");
        }

        EscalationGrantValidator.Outcome outcome = grantValidator.validate(
                request.tenantId(), request.actorId(), request.escalationGrantId());
        Objects.requireNonNull(outcome, "grantValidator returned null outcome");

        return switch (outcome) {
            case VALID -> {
                log.warn("BREAK-GLASS (GOVERNED): action={} subject={} actor={} purpose={} grant={} "
                                + "— emergency access allowed against a confirmed escalation grant.",
                        request.action(), request.subjectRef(), request.actorId(),
                        request.purposeOfUse(), request.escalationGrantId());
                yield new Decision(true, true, "BREAK_GLASS", request.purposeOfUse(),
                        request.action(), request.subjectRef());
            }
            case NO_GRANT -> {
                log.warn("BREAK-GLASS REFUSED: action={} subject={} actor={} — no active escalation "
                                + "grant. An emergency purpose-of-use is not authority on its own.",
                        request.action(), request.subjectRef(), request.actorId());
                throw new EmergencyAccessDeniedException(
                        "Emergency action '" + request.action() + "' requires an active escalation "
                                + "grant. The purpose-of-use presented is accepted, but no grant is "
                                + "active for this actor — request emergency access through the "
                                + "break-glass workflow, which can approve one.");
            }
            case UNREACHABLE -> {
                // Allowed by ruling, and only because it is recorded. Record BEFORE returning, so a
                // later failure cannot produce an allow with no record.
                String reason = "Escalation grant could not be verified: the trust plane was "
                        + "unreachable. Allowed under the emergency-access ruling; this override was "
                        + "NOT governed by a grant and requires post-hoc review.";
                overrideRecorder.record(new UngovernedOverrideRecorder.UngovernedOverride(
                        request.tenantId(), request.actorId(), request.action(),
                        request.subjectRef(), request.purposeOfUse(),
                        request.escalationGrantId(), reason));
                log.error("BREAK-GLASS (UNGOVERNED): action={} subject={} actor={} purpose={} "
                                + "grant={} — trust plane unreachable, emergency access ALLOWED "
                                + "without a confirmed grant. Recorded for post-hoc review.",
                        request.action(), request.subjectRef(), request.actorId(),
                        request.purposeOfUse(), request.escalationGrantId());
                yield new Decision(true, false, "BREAK_GLASS_UNGOVERNED", request.purposeOfUse(),
                        request.action(), request.subjectRef());
            }
        };
    }

    /**
     * What is being attempted, and the trust context to check it against.
     *
     * @param escalationGrantId may be null — that is a refusal, not a reason to skip the check
     */
    public record EmergencyAccessRequest(
            String tenantId,
            String actorId,
            String purposeOfUse,
            String action,
            String subjectRef,
            String escalationGrantId) {
    }

    /**
     * The outcome of an emergency-access evaluation (all fields non-secret; safe to persist/audit).
     *
     * @param governed whether a grant was actually confirmed. False means the trust plane could not
     *                 be asked and an ungoverned override was recorded — a caller that surfaces
     *                 break-glass state to a user should say so.
     */
    public record Decision(boolean breakGlass, boolean governed, String mode, String purposeOfUse,
                           String action, String subjectRef) {
    }

    /** Thrown when an emergency action is attempted without emergency purpose, or without a grant. */
    public static class EmergencyAccessDeniedException extends RuntimeException {
        public EmergencyAccessDeniedException(String message) {
            super(message);
        }
    }
}
