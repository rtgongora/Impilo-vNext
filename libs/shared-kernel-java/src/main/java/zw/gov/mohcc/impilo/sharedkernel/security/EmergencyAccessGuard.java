package zw.gov.mohcc.impilo.sharedkernel.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Shared emergency-access (break-glass) guard for high-risk clinical actions — MADI O-negative and
 * uncrossmatched blood release, massive haemorrhage protocol activation and pack issue.
 *
 * <h2>What this guard enforces</h2>
 *
 * <p>An emergency action is permitted only when <b>both</b> hold:</p>
 * <ol>
 *   <li>the caller asserts an emergency purpose-of-use, <em>and</em></li>
 *   <li>the trust plane confirms the actor holds an active break-glass grant — or could not be
 *       asked.</li>
 * </ol>
 *
 * <p>The first condition alone used to be the whole guard. Purpose-of-use is client-supplied on
 * this path (madi is not behind Envoy's ext_authz — Envoy defines two clusters, both
 * experience-bff, and routes to no domain service), so anyone able to set
 * {@code X-Purpose-Of-Use: BREAK_GLASS} satisfied it, and break-glass unlocked emergency clinical
 * access with no emergency, no approval and no grant. The purpose now only <em>selects</em> the
 * emergency path; it no longer authorises it.</p>
 *
 * <h2>The three branches, and the PO ruling behind them</h2>
 *
 * <table>
 *   <caption>Outcome by grant verdict</caption>
 *   <tr><th>Verdict</th><th>Outcome</th></tr>
 *   <tr><td>{@link BreakGlassGrantVerdict#VALID}</td><td>allow; ordinary elevated break-glass audit</td></tr>
 *   <tr><td>{@link BreakGlassGrantVerdict#NO_GRANT}</td><td><b>refuse</b></td></tr>
 *   <tr><td>{@link BreakGlassGrantVerdict#UNREACHABLE}</td><td>allow, <b>only after</b> an ungoverned-override record is durably written, carrying a named post-hoc review obligation</td></tr>
 * </table>
 *
 * <p>Escalated to the product owner on 2026-08-07 as a patient-safety decision rather than a
 * refactor, and ruled on there. "Fail closed always" was considered and rejected: the affected path
 * includes uncrossmatched blood release, and refusing a transfusion because a policy service is
 * unreachable is a different kind of harm from allowing an ungoverned one. The cost of that choice
 * is that an outage produces allowed-but-unvalidated actions, which is why the third branch is
 * obliged to leave a record — and why the record is written before the allow, not after.</p>
 *
 * <p>The two failure modes this guard is built to avoid are symmetric, and both are real:
 * treating an outage as a refusal denies emergency care; treating a refusal as an outage restores
 * the forged-purpose hole under a new name. {@link BreakGlassGrantClient} and
 * {@link UngovernedOverrideRecorder} carry the contracts that keep them apart.</p>
 */
public final class EmergencyAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(EmergencyAccessGuard.class);

    /**
     * Fixed marker on the log line for an allowed-but-unvalidated override. Greppable on purpose:
     * the durable record is the outbox row, but an operator watching logs during an incident needs
     * to see these going past.
     */
    public static final String UNGOVERNED_OVERRIDE_MARKER = "UNGOVERNED_BREAK_GLASS_OVERRIDE";

    private EmergencyAccessGuard() {
    }

    /** Whether the caller is operating under an emergency / break-glass purpose of use. */
    public static boolean isEmergencyPurpose(String purposeOfUse) {
        if (purposeOfUse == null) return false;
        String p = purposeOfUse.trim().toUpperCase();
        return p.equals("EMERGENCY") || p.equals("BREAK_GLASS");
    }

    /**
     * Authorise a high-risk emergency action against the real grant model.
     *
     * <p>{@code grantClient} and {@code recorder} are required parameters rather than optional
     * collaborators with null-tolerant defaults. A guard that degrades to the old string comparison
     * when its dependencies are missing would reintroduce the exposure on any call site that forgot
     * to wire them, and would do it invisibly.</p>
     *
     * @throws EmergencyAccessDeniedException when the purpose is not emergency/break-glass, or when
     *                                        the trust plane reported no active grant.
     * @return an audited {@link Decision}. {@code governed=false} means the grant could not be
     *         checked and an ungoverned override was recorded.
     */
    public static Decision requireBreakGlass(BreakGlassGrantClient grantClient,
                                             UngovernedOverrideRecorder recorder,
                                             BreakGlassGrantQuery query) {
        Objects.requireNonNull(grantClient, "grantClient is required — see the class javadoc");
        Objects.requireNonNull(recorder, "recorder is required — see the class javadoc");
        Objects.requireNonNull(query, "query is required");

        if (!isEmergencyPurpose(query.purposeOfUse())) {
            throw new EmergencyAccessDeniedException(
                    "Emergency action '" + query.action() + "' requires EMERGENCY/BREAK_GLASS purpose-of-use (got: "
                            + (query.purposeOfUse() == null ? "none" : query.purposeOfUse()) + ")");
        }

        BreakGlassGrantCheck check = grantClient.check(query);

        switch (check.verdict()) {
            case VALID -> {
                log.warn("BREAK-GLASS EMERGENCY ACCESS (grant validated): action={} subject={} actor={} "
                                + "purpose={} grantSource={} grantRef={} — governed override, elevated audit.",
                        query.action(), query.subjectRef(), query.actorId(), query.purposeOfUse(),
                        check.source(), check.grantRef());
                return new Decision(true, true, "BREAK_GLASS",
                        Objects.requireNonNullElse(query.purposeOfUse(), "EMERGENCY"),
                        query.action(), query.subjectRef(), check.grantRef());
            }
            case NO_GRANT -> {
                log.warn("BREAK-GLASS REFUSED (no grant): action={} subject={} actor={} purpose={} — the trust "
                                + "plane was reached and reported no active break-glass grant for this actor.",
                        query.action(), query.subjectRef(), query.actorId(), query.purposeOfUse());
                throw new EmergencyAccessDeniedException(
                        "Emergency action '" + query.action() + "' requires an active break-glass grant. "
                                + "No grant is held by this actor. Request one via POST /v1/break-glass, "
                                + "which records the clinical reason and schedules supervisor review.");
            }
            case UNREACHABLE -> {
                // Record FIRST. If this throws, the action is refused and no allow is returned —
                // an allow without a record is strictly worse than a refusal, so there is
                // deliberately no catch here. See UngovernedOverrideRecorder's contract.
                UngovernedOverride override = UngovernedOverride.from(query, check);
                recorder.record(override);

                log.warn("{}: action={} subject={} actor={} purpose={} reason={} — the break-glass grant could "
                                + "NOT be checked. Access was ALLOWED under the emergency-care ruling and is "
                                + "UNVALIDATED. {}",
                        UNGOVERNED_OVERRIDE_MARKER, query.action(), query.subjectRef(), query.actorId(),
                        query.purposeOfUse(), check.detail(), override.reviewObligation());

                return new Decision(true, false, "BREAK_GLASS_UNGOVERNED",
                        Objects.requireNonNullElse(query.purposeOfUse(), "EMERGENCY"),
                        query.action(), query.subjectRef(), null);
            }
            default -> throw new IllegalStateException("Unhandled grant verdict: " + check.verdict());
        }
    }

    /**
     * The outcome of an emergency-access evaluation (all fields non-secret; safe to persist/audit).
     *
     * @param breakGlass always true on success — this is a break-glass decision
     * @param governed   whether a grant was actually validated. False means the trust plane could
     *                   not be asked and an ungoverned override was recorded; a caller that
     *                   persists this should persist the distinction too
     * @param grantRef   the validated grant's identifier, or null when ungoverned
     */
    public record Decision(boolean breakGlass, boolean governed, String mode, String purposeOfUse,
                           String action, String subjectRef, String grantRef) {}

    /** Thrown when an emergency action is refused — wrong purpose, or no grant. */
    public static class EmergencyAccessDeniedException extends RuntimeException {
        public EmergencyAccessDeniedException(String message) {
            super(message);
        }
    }
}
