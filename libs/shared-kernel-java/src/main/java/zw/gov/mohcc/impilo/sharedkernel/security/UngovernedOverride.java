package zw.gov.mohcc.impilo.sharedkernel.security;

import java.time.Instant;
import java.util.Objects;

/**
 * An emergency action that was allowed <em>without</em> a validated grant, because the trust plane
 * could not be reached.
 *
 * <p>This is not an access log line. It is the record of a control that did not run: clinical
 * access was granted on the strength of a client-supplied purpose-of-use and nothing else, because
 * the thing that would have checked it was unavailable. Every one of these needs a human to look at
 * it afterwards and decide whether the emergency was real.</p>
 *
 * <p>{@link #reviewObligation()} names who owes that review. It is a required field rather than a
 * nice-to-have: an override recorded with no named owner is a record nobody is responsible for
 * reading, which in practice is the same as no record.</p>
 *
 * @param tenantId          tenant the override happened in
 * @param actorId           who performed the ungoverned action
 * @param action            what they did, e.g. {@code O_NEG_EMERGENCY_RELEASE}
 * @param subjectRef        the patient it was done for
 * @param purposeOfUse      the unverified purpose-of-use that selected the emergency path
 * @param grantToken        the grant token supplied, if any — recorded unvalidated, and labelled so
 * @param reason            why the grant could not be checked; comes from
 *                          {@link BreakGlassGrantCheck#detail()}
 * @param reviewObligation  who must review this, and by when
 * @param occurredAt        when it happened
 */
public record UngovernedOverride(
        String tenantId,
        String actorId,
        String action,
        String subjectRef,
        String purposeOfUse,
        String grantToken,
        String reason,
        String reviewObligation,
        Instant occurredAt
) {

    /**
     * The event type these records carry on the wire. Fixed and greppable on purpose: it is how an
     * on-call reviewer finds every ungoverned override in the estate, and how a query
     * distinguishes them from ordinary break-glass activity that <em>was</em> governed.
     */
    public static final String EVENT_TYPE = "BREAK_GLASS_UNGOVERNED_OVERRIDE";

    /** The default obligation attached when a caller does not name a more specific one. */
    public static final String DEFAULT_REVIEW_OBLIGATION =
            "MANDATORY_POST_HOC_REVIEW: facility clinical governance lead must review this override "
                    + "within 24 hours and confirm the emergency was genuine";

    public UngovernedOverride {
        Objects.requireNonNull(actorId, "actorId is required");
        Objects.requireNonNull(action, "action is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        reviewObligation = (reviewObligation == null || reviewObligation.isBlank())
                ? DEFAULT_REVIEW_OBLIGATION
                : reviewObligation;
    }

    /** Build the record for a guard that allowed {@code query} after an UNREACHABLE check. */
    public static UngovernedOverride from(BreakGlassGrantQuery query, BreakGlassGrantCheck check) {
        return new UngovernedOverride(
                query.tenantId(),
                query.actorId(),
                query.action(),
                query.subjectRef(),
                query.purposeOfUse(),
                query.grantToken(),
                check.detail(),
                DEFAULT_REVIEW_OBLIGATION,
                Instant.now());
    }
}
