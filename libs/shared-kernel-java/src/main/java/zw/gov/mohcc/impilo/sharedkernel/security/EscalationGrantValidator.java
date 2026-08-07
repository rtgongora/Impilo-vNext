package zw.gov.mohcc.impilo.sharedkernel.security;

/**
 * Asks the trust plane whether an escalation grant holds, and reports which of three things
 * happened.
 *
 * <h2>Why three outcomes and not two</h2>
 * <p>The whole point of this interface is that <b>"refused" and "could not ask" are different
 * answers</b>. A grant that does not exist means the caller has no authority and must be refused. A
 * grant service that cannot be reached means nothing about the caller's authority at all — and on
 * the paths this guards, which include uncrossmatched and O-negative blood release, refusing on that
 * basis would let a tshepo-authz outage block a transfusion.</p>
 *
 * <p>So an implementation <b>must not</b> have a catch-all that turns a transport failure into
 * {@link Outcome#NO_GRANT}. That single line is the difference between this being a real control and
 * being another control that looks like validation and is not — the exact defect the break-glass
 * write-up documented. If you cannot tell a 404 from a connection reset, you cannot implement this
 * interface correctly.</p>
 */
@FunctionalInterface
public interface EscalationGrantValidator {

    /** What the trust plane said, or that it could not be asked. */
    enum Outcome {
        /** A grant is active for this actor, in this tenant, now. */
        VALID,
        /**
         * The trust plane answered, and the answer is no. Absent, malformed, expired, revoked, or
         * bound to a different actor — deliberately indistinguishable, so this cannot be used to
         * probe which grants exist.
         */
        NO_GRANT,
        /**
         * The trust plane could not be asked: connection refused, timeout, 5xx, malformed response.
         * <b>Not</b> an answer about the caller. Callers treat this as its own case and never fold
         * it into {@link #NO_GRANT}.
         */
        UNREACHABLE
    }

    /**
     * @param tenantId the tenant the grant must belong to
     * @param actorId  the actor the grant must be bound to
     * @param grantId  the grant token presented, may be null or blank — that is {@link Outcome#NO_GRANT},
     *                 not a reason to skip the check
     * @return which of the three cases holds; never null
     */
    Outcome validate(String tenantId, String actorId, String grantId);
}
