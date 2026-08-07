package zw.gov.mohcc.impilo.sharedkernel.security;

import java.util.Objects;

/**
 * The result of asking the trust plane whether an actor holds a break-glass grant.
 *
 * <h2>Three outcomes, and why they are values rather than an exception plus a default</h2>
 *
 * <p>The obvious shape for this is a boolean plus a thrown exception on failure. That shape is the
 * bug. Every {@code catch} around it collapses "the trust plane said no" into "the trust plane did
 * not answer", and a guard that cannot tell those apart is a control that looks like validation and
 * is not — precisely the defect being fixed here. So the outcomes are three enumerated values on a
 * returned object, and there is no exceptional path to catch:</p>
 *
 * <ul>
 *   <li>{@link BreakGlassGrantVerdict#VALID} — a grant was found. Proceed.</li>
 *   <li>{@link BreakGlassGrantVerdict#NO_GRANT} — the trust plane was asked and answered that there
 *       is no grant. <b>Refuse.</b> This is a decision, and it is only ever produced by a successful
 *       response that said so.</li>
 *   <li>{@link BreakGlassGrantVerdict#UNREACHABLE} — the question could not be put. <b>Allow</b>, and
 *       record an ungoverned override for post-hoc review. This is the absence of a decision, and it
 *       must never be manufactured from anything that could also mean NO_GRANT.</li>
 * </ul>
 *
 * <p>The PO ruling of 2026-08-07 sets that asymmetry deliberately: refusing an emergency
 * transfusion because tshepo-authz is down is a different kind of harm from allowing an ungoverned
 * one, and "fail closed always" was considered and rejected for that reason. The cost of the choice
 * is that an outage produces allowed-but-ungoverned actions — which is exactly why the UNREACHABLE
 * branch is obliged to leave a record someone will read.</p>
 *
 * @param verdict  which of the three outcomes occurred
 * @param source   which grant model answered, when one did; null otherwise
 * @param grantRef identifier of the grant that answered, for the audit trail; null otherwise
 * @param detail   human-readable context — on UNREACHABLE, why the question could not be put
 */
public record BreakGlassGrantCheck(
        BreakGlassGrantVerdict verdict,
        String source,
        String grantRef,
        String detail
) {

    public BreakGlassGrantCheck {
        Objects.requireNonNull(verdict, "verdict is required");
    }

    public static BreakGlassGrantCheck valid(String source, String grantRef) {
        return new BreakGlassGrantCheck(BreakGlassGrantVerdict.VALID, source, grantRef, null);
    }

    public static BreakGlassGrantCheck noGrant() {
        return new BreakGlassGrantCheck(BreakGlassGrantVerdict.NO_GRANT, null, null,
                "the trust plane was reached and reported no active grant");
    }

    /**
     * @param detail why the trust plane could not be asked. Always supply something specific — this
     *               string is what an on-call reviewer reads to decide whether an override was a
     *               genuine outage or a misconfiguration nobody noticed.
     */
    public static BreakGlassGrantCheck unreachable(String detail) {
        return new BreakGlassGrantCheck(BreakGlassGrantVerdict.UNREACHABLE, null, null,
                Objects.requireNonNullElse(detail, "the trust plane could not be reached"));
    }

    public boolean isValid() {
        return verdict == BreakGlassGrantVerdict.VALID;
    }

    public boolean isUnreachable() {
        return verdict == BreakGlassGrantVerdict.UNREACHABLE;
    }
}
