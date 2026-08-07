package zw.gov.mohcc.impilo.sharedkernel.security;

/**
 * The three outcomes of a break-glass grant check.
 *
 * <p>Two of these are answers and one is the absence of an answer. Keeping the third in the same
 * enumeration — rather than expressing it as a thrown exception — is what stops a caller from
 * quietly treating an outage as a refusal, or a refusal as an outage.</p>
 *
 * @see BreakGlassGrantCheck
 */
public enum BreakGlassGrantVerdict {

    /** An active grant was found for this actor in this tenant. */
    VALID,

    /**
     * The trust plane was reached and reported no active grant. A decision — refuse.
     *
     * <p>Only ever produced by a successful response that said so. A transport failure, a timeout,
     * a 401, a 404 or a 5xx is never this value.</p>
     */
    NO_GRANT,

    /**
     * The trust plane could not be asked. Not a decision — allow, and record an ungoverned
     * override with a post-hoc review obligation.
     */
    UNREACHABLE
}
