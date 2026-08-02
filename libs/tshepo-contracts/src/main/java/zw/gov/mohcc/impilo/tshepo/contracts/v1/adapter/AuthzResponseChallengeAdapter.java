package zw.gov.mohcc.impilo.tshepo.contracts.v1.adapter;

import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeDecision;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeOutcome;

import java.util.List;
import java.util.Map;

/**
 * Compatibility between legacy {@link AuthzResponse}/{@link Verdict} and canonical
 * {@link TrustChallengeOutcome}.
 *
 * <p>Legacy only knows ALLOW / DENY / STEP_UP_REQUIRED. Other canonical decisions cannot be
 * represented without loss; reverse mapping refuses rather than inventing broader authority.</p>
 */
@CompatibilityAdapterMeta(
        owner = "tshepo-authz-service",
        provenCallers = {
                "services/tshepo-authz-service/.../core/PolicyEngine.java",
                "services/tshepo-authz-service/.../api/AuthorizeController.java",
                "services/tshepo-authz-service/.../grpc/ExtAuthzGrpcService.java",
                "ui/one-ui-shell/src/lib/contracts.ts (AuthzResponse shadow)",
                "ui/one-ui-shell/src/hooks/usePolicyDecision.ts"
        },
        legacyShape = "AuthzResponse + Verdict{ALLOW,DENY,STEP_UP_REQUIRED}",
        canonicalShape = "TrustChallengeOutcome + TrustChallengeDecision (12 outcomes)",
        removalCondition = "All proven callers consume TrustChallengeOutcome; AuthzResponse retired after UX challenge cohort."
)
public final class AuthzResponseChallengeAdapter {

    private AuthzResponseChallengeAdapter() {}

    public static TrustChallengeOutcome toCanonical(AuthzResponse legacy, String decisionId, String policyVersion) {
        if (legacy == null || legacy.verdict() == null) {
            return TrustChallengeOutcome.deny("MISSING_VERDICT", "trust.deny.missing_verdict", decisionId);
        }
        return switch (legacy.verdict()) {
            case ALLOW -> TrustChallengeOutcome.allow(decisionId, policyVersion);
            case DENY -> denyOrChallenge(legacy, decisionId, policyVersion);
            case STEP_UP_REQUIRED -> TrustChallengeOutcome.of(
                    TrustChallengeDecision.STEP_UP_REQUIRED,
                    "STEP_UP_REQUIRED",
                    "trust.step_up.required",
                    "STEP_UP",
                    legacy.stepUpRequirement() != null ? legacy.stepUpRequirement().requiredAal() : 2,
                    legacy.stepUpMethods() == null ? List.of() : legacy.stepUpMethods(),
                    List.of(),
                    null,
                    null,
                    null,
                    decisionId,
                    policyVersion,
                    null,
                    null);
        };
    }

    /**
     * Legacy deny codes that name an ACTIONABLE outcome rather than a refusal.
     *
     * <p>The legacy wire has three verdicts (ALLOW / DENY / STEP_UP_REQUIRED), so a decision like
     * "no consent has been granted yet" can only travel as a DENY carrying a specific error code.
     * Flattening every DENY into {@code TrustChallengeDecision.DENY} throws that distinction away
     * at the boundary — and the distinction is the whole point: "you may not" and "nobody has
     * asked you yet" are different answers, and only one of them has a next step.</p>
     *
     * <p>Deliberately an allowlist. An unrecognised code stays a DENY, so a new refusal reason can
     * never be silently promoted into something the UI invites the user to act on.</p>
     */
    private static final Map<String, ChallengeMapping> ACTIONABLE_DENY_CODES = Map.of(
            "CONSENT_REQUIRED", new ChallengeMapping(
                    TrustChallengeDecision.CONSENT_REQUIRED, "trust.consent.required", "OBTAIN_CONSENT"),
            "RECOVERY_REQUIRED", new ChallengeMapping(
                    TrustChallengeDecision.RECOVERY_REQUIRED, "trust.recovery.required", "COMPLETE_RECOVERY"));

    private record ChallengeMapping(TrustChallengeDecision decision, String messageKey, String action) {}

    private static TrustChallengeOutcome denyOrChallenge(
            AuthzResponse legacy, String decisionId, String policyVersion) {
        String code = legacy.errorCode() == null ? "DENIED" : legacy.errorCode();
        ChallengeMapping mapping = ACTIONABLE_DENY_CODES.get(code);
        if (mapping == null) {
            return TrustChallengeOutcome.deny(code, "trust.deny.generic", decisionId);
        }
        return TrustChallengeOutcome.of(
                mapping.decision(), code, mapping.messageKey(), mapping.action(),
                null, List.of(), List.of(), null, null, null,
                decisionId, policyVersion, null, null);
    }

    public static AuthzResponse toLegacy(TrustChallengeOutcome canonical) {
        if (canonical == null || canonical.decision() == null) {
            return AuthzResponse.deny("MISSING_DECISION", "Missing trust challenge decision", 0);
        }
        return switch (canonical.decision()) {
            case ALLOW -> AuthzResponse.allow(null, 0, null);
            case DENY -> AuthzResponse.deny(
                    canonical.reasonCode() == null ? "DENIED" : canonical.reasonCode(),
                    canonical.userMessageKey() == null ? "Denied" : canonical.userMessageKey(),
                    0);
            case STEP_UP_REQUIRED -> AuthzResponse.stepUp(
                    canonical.allowedAuthenticationMethods(), 0);
            case AUTHENTICATION_REQUIRED,
                 CONTEXT_REQUIRED,
                 AUTHORITY_REQUIRED,
                 CONSENT_REQUIRED,
                 APPROVAL_REQUIRED,
                 BREAK_GLASS_AVAILABLE,
                 REAUTHENTICATION_REQUIRED,
                 RECOVERY_REQUIRED,
                 TEMPORARILY_UNAVAILABLE -> throw new UnsupportedOperationException(
                    "Cannot represent " + canonical.decision()
                            + " in legacy AuthzResponse without broadening or inventing authority");
        };
    }

    /**
     * Total reverse mapping for live legacy request paths. Unlike {@link #toLegacy}, this never
     * throws: canonical outcomes the legacy wire cannot represent are mapped to a fail-closed
     * legacy DENY carrying {@code UNREPRESENTABLE_<decision>} as the error code.
     *
     * <p>Expected legacy HTTP behavior: a legacy caller receiving this response follows its
     * ordinary DENY path (HTTP 403 from AuthorizeController / ext_authz deny), never an
     * unhandled 500 and never an accidental ALLOW.</p>
     */
    public static AuthzResponse toLegacySafe(TrustChallengeOutcome canonical) {
        try {
            return toLegacy(canonical);
        } catch (UnsupportedOperationException unrepresentable) {
            return AuthzResponse.deny(
                    "UNREPRESENTABLE_" + canonical.decision().name(),
                    "Action requires the trust challenge experience; legacy channel denies by default",
                    0);
        }
    }
}
