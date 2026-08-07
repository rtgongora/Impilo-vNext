package zw.gov.mohcc.impilo.sharedkernel.security;

/**
 * Records that an emergency action proceeded <b>without</b> a grant being confirmed, because the
 * trust plane could not be asked.
 *
 * <h2>Why this is a required collaborator and not the caller's job</h2>
 * <p>The PO ruling that allows this case attaches an obligation to it: someone must be able to find
 * these afterwards and review them. An allow that is not recorded is indistinguishable from an allow
 * that was governed, which would make the ruling unauditable and the whole distinction pointless.</p>
 *
 * <p>So {@link EmergencyAccessGuard} takes a recorder and calls it itself, rather than returning a
 * flag and trusting each call site to write the record. A caller that forgets is the likely failure,
 * and it fails silently and permissively — the worst combination. Making it the guard's
 * responsibility means the test can assert the write, which is the assertion that actually matters
 * on this branch.</p>
 *
 * <h2>What an implementation owes</h2>
 * <p>The record must be legible as an <em>ungoverned override</em> — not a normal access log line
 * that happens to mention break-glass. Whoever runs the post-hoc review has to be able to select
 * exactly these. It should also be durable without a synchronous network hop: an implementation that
 * calls a remote audit service is calling out during precisely the conditions under which remote
 * calls are failing, and losing the record for the same reason the grant check failed.</p>
 */
@FunctionalInterface
public interface UngovernedOverrideRecorder {

    /**
     * @param override what happened, who did it, and why it could not be governed; never null
     */
    void record(UngovernedOverride override);

    /**
     * An emergency action taken without a confirmed grant.
     *
     * @param tenantId    tenant the action was taken in
     * @param actorId     who took it
     * @param action      what was done (e.g. {@code O_NEG_EMERGENCY_RELEASE})
     * @param subjectRef  who it was done to
     * @param purposeOfUse the emergency purpose presented
     * @param grantId     the grant token presented, if any — recorded because "presented a grant we
     *                    could not verify" and "presented nothing" are different situations for a
     *                    reviewer, even though both proceeded
     * @param reason      why the grant could not be confirmed, in words a reviewer can act on
     */
    record UngovernedOverride(
            String tenantId,
            String actorId,
            String action,
            String subjectRef,
            String purposeOfUse,
            String grantId,
            String reason) {
    }
}
