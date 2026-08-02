package zw.gov.mohcc.impilo.experience.client;

import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeDecision;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeOutcome;

/**
 * What the PDP actually said, instead of whether it said ALLOW.
 *
 * <p>{@code TshepoAuthzServiceClient.syntheticAuthorizeVerdict} returns a {@code boolean}, so every
 * decision detail — reason code, required assurance, permitted step-up methods, any continuation —
 * is discarded before a single BFF controller sees it. That is why no BFF endpoint can emit a
 * trust challenge: by the time the decision reaches one, the only surviving fact is "not allowed".</p>
 *
 * <h2>The conflation this exists to end</h2>
 *
 * <p>That method also returns {@code false} from its catch-all. <strong>A PDP outage and a
 * deliberate refusal are therefore the same value.</strong> The trust doctrine requires the
 * opposite: <em>"A denial, an authentication failure, and a temporary infrastructure failure are
 * distinct outcomes and must never be conflated."</em> A user told "you do not have access" when
 * the truth is "the policy service is down" will go and ask for permissions they already have.</p>
 *
 * <p>All three still refuse the action. This governs what the caller is <em>told</em>.</p>
 */
public record TrustDecisionResult(TrustChallengeOutcome outcome, boolean transportFailure) {

    /** The PDP answered. Its answer may still be a refusal. */
    public static TrustDecisionResult of(TrustChallengeOutcome outcome) {
        return new TrustDecisionResult(outcome, false);
    }

    /**
     * The PDP could not be reached or did not answer usably.
     *
     * <p>Fails closed — {@link #allowed()} is false — but is reported as
     * {@link TrustChallengeDecision#TEMPORARILY_UNAVAILABLE} so it is never presented as a
     * permissions problem.
     */
    public static TrustDecisionResult unavailable(String reasonCode) {
        return new TrustDecisionResult(
                TrustChallengeOutcome.of(
                        TrustChallengeDecision.TEMPORARILY_UNAVAILABLE,
                        reasonCode,
                        "trust.unavailable.retry",
                        "RETRY",
                        null, java.util.List.of(), java.util.List.of(),
                        null, null, null, null, null, null, null),
                true);
    }

    public boolean allowed() {
        return !transportFailure
                && outcome != null
                && outcome.decision() == TrustChallengeDecision.ALLOW;
    }

    /**
     * Whether the caller can do something about this. A refusal that no action resolves must not
     * be presented with a call to action, and an outage must not be presented as a refusal.
     */
    public boolean actionable() {
        if (outcome == null || transportFailure) {
            return false;
        }
        return switch (outcome.decision()) {
            case AUTHENTICATION_REQUIRED, STEP_UP_REQUIRED, CONTEXT_REQUIRED, AUTHORITY_REQUIRED,
                 CONSENT_REQUIRED, APPROVAL_REQUIRED, BREAK_GLASS_AVAILABLE,
                 REAUTHENTICATION_REQUIRED, RECOVERY_REQUIRED -> true;
            case ALLOW, DENY, TEMPORARILY_UNAVAILABLE -> false;
        };
    }

    public TrustChallengeDecision decision() {
        return outcome == null ? TrustChallengeDecision.DENY : outcome.decision();
    }
}
