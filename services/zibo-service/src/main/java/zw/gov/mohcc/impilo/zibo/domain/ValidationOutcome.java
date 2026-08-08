package zw.gov.mohcc.impilo.zibo.domain;

/**
 * What actually happened to one coding, independent of how loudly the policy mode chose to complain.
 *
 * <p>Severity and outcome are different questions and conflating them is why this estate could not
 * measure itself. {@link ValidationSeverity} answers "how much noise should this make", which
 * depends on whether the scope is STRICT or LENIENT. This answers "did the code resolve", which
 * does not. The same unknown code is a WARNING in one facility and an ERROR in another; it is
 * {@link #UNKNOWN_CODE} in both.</p>
 *
 * <p>{@link #RESOLVED} is the reason this enum exists. Before it, only failures were recorded, so
 * the log could never produce a percentage — a hundred failures might be a catastrophe or a rounding
 * error, and nothing on record could tell you which.</p>
 *
 * <p>Append-only, like {@link ArtifactType}: these values are persisted.</p>
 */
public enum ValidationOutcome {

    /** The system was found and the code exists in it. The denominator. */
    RESOLVED,

    /** No governed artifact claims this system URI at all. */
    UNKNOWN_SYSTEM,

    /** The system resolved; the code is not in it. */
    UNKNOWN_CODE,

    /** The code exists but is not a member of the ValueSet it was validated against. */
    INVALID_MEMBERSHIP,

    /** A translation was requested and no mapping exists between the two systems. */
    UNRESOLVED_MAPPING,

    /**
     * Validation could not be performed — malformed payload, or the engine failed. Deliberately
     * distinct from every other value: not knowing is not the same as knowing the code is bad, and
     * counting these as failures would overstate the coding gap.
     */
    NOT_EVALUATED
}
