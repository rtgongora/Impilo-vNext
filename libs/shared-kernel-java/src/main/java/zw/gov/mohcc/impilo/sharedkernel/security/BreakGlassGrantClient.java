package zw.gov.mohcc.impilo.sharedkernel.security;

/**
 * Asks the trust plane whether an actor holds a break-glass grant.
 *
 * <h2>Implementation contract — the load-bearing part</h2>
 *
 * <p><b>An implementation MUST NOT throw.</b> Not for a timeout, not for a connection refusal, not
 * for a 401, not for a malformed response body. Every failure to obtain an answer is returned as
 * {@link BreakGlassGrantVerdict#UNREACHABLE} with a specific {@code detail}.</p>
 *
 * <p>The reason is narrow and worth stating plainly. If this method can throw, every call site
 * grows a {@code catch}, and the only sensible-looking thing to write inside that {@code catch} is
 * "treat it as no grant". At that moment the guard has stopped distinguishing "the trust plane
 * refused" from "the trust plane did not answer", and the control is back to being a thing that
 * looks like validation and is not. The distinction cannot be maintained by discipline at each call
 * site; it has to be impossible to lose, so the exceptional path is removed rather than documented.</p>
 *
 * <p><b>{@link BreakGlassGrantVerdict#NO_GRANT} MUST come only from a successful response that said
 * so.</b> An implementation that maps a 404, a 401, a connection error or an unparseable body to
 * NO_GRANT has rebuilt the defect in the opposite direction — it would refuse emergency care
 * because of a misconfigured URL or an expired service credential. Those are all UNREACHABLE.</p>
 *
 * @see BreakGlassGrantCheck
 */
@FunctionalInterface
public interface BreakGlassGrantClient {

    /**
     * @return never null; never throws. {@link BreakGlassGrantVerdict#UNREACHABLE} whenever an
     *         answer could not be obtained, for any reason.
     */
    BreakGlassGrantCheck check(BreakGlassGrantQuery query);
}
