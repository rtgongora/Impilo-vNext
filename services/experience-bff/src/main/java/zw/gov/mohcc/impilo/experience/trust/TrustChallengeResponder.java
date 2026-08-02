package zw.gov.mohcc.impilo.experience.trust;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.auth.session.WebAuthSessionStore;
import zw.gov.mohcc.impilo.experience.client.TrustDecisionResult;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeDecision;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeOutcome;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a PDP decision into an HTTP outcome, in one place.
 *
 * <p>Two things every governance service in this module got wrong, identically, because each one
 * hand-rolled its refusal:</p>
 *
 * <ol>
 *   <li><b>Status.</b> Everything was 403. A PDP outage answered 403 too, so the browser could not
 *       tell "you may not" from "we could not ask" — and neither could the person reading it.</li>
 *   <li><b>Body.</b> A prose sentence written for a log ("Tshepo PDP denied telemedicine read").
 *       No reason code, no permitted next step, no continuation, nothing renderable.</li>
 * </ol>
 *
 * <p>A continuation is minted <em>only</em> for actionable outcomes. Issuing one for a flat DENY
 * would hand the shell a resume token for a journey that has no next step, which invites exactly
 * the fabricated "try again" affordance this checkpoint exists to remove.</p>
 */
@Component
public class TrustChallengeResponder {

    private static final Logger log = LoggerFactory.getLogger(TrustChallengeResponder.class);

    private final WebAuthSessionStore sessionStore;

    public TrustChallengeResponder(WebAuthSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    /**
     * Refuses the request with the PDP's own reasoning, or returns normally if it allowed.
     *
     * @param returnTo where the caller was going, so an actionable challenge can resume it. Null
     *                 suppresses continuation minting rather than inventing a destination.
     */
    public void enforce(TrustDecisionResult decision, String returnTo, String logMessage) {
        if (decision.allowed()) {
            return;
        }
        TrustChallengeOutcome outcome = decision.outcome();
        if (decision.actionable() && returnTo != null && !returnTo.isBlank()) {
            outcome = withContinuation(outcome, returnTo);
        }
        log.warn("{}: decision={} reason={}", logMessage, decision.decision(),
                outcome == null ? null : outcome.reasonCode());
        throw new TrustChallengeException(outcome, logMessage);
    }

    private TrustChallengeOutcome withContinuation(TrustChallengeOutcome outcome, String returnTo) {
        try {
            Map<String, String> state = new LinkedHashMap<>();
            if (outcome.reasonCode() != null) state.put("reasonCode", outcome.reasonCode());
            if (outcome.requiredAction() != null) state.put("requiredAction", outcome.requiredAction());
            if (outcome.decisionId() != null) state.put("decisionId", outcome.decisionId());
            if (outcome.requiredAssurance() != null) {
                state.put("requiredAssurance", String.valueOf(outcome.requiredAssurance()));
            }
            String id = sessionStore.saveContinuation(returnTo, outcome.decision().name(), state);
            return outcome.withContinuationReference(id);
        } catch (Exception e) {
            // A continuation is an affordance, not a control. If Redis is unavailable the refusal
            // still stands and is still explained -- it simply cannot offer a resume.
            log.warn("Could not mint continuation for {}: {}", outcome.decision(), e.getMessage());
            return outcome;
        }
    }

    /**
     * The HTTP status a decision deserves.
     *
     * <p>Collapsing all of these to 403 is what made an outage indistinguishable from a refusal at
     * the wire level, before any UI got a chance to tell them apart.</p>
     */
    public static HttpStatus statusFor(TrustChallengeDecision decision) {
        if (decision == null) {
            return HttpStatus.FORBIDDEN;
        }
        return switch (decision) {
            case ALLOW -> HttpStatus.OK;
            // Not authenticated *enough*, or not at all: the browser must be told to authenticate,
            // which is a 401 concern, not an authorization failure.
            case AUTHENTICATION_REQUIRED, REAUTHENTICATION_REQUIRED, STEP_UP_REQUIRED -> HttpStatus.UNAUTHORIZED;
            // The PDP could not answer. 503 is the only honest reading, and it is the one status
            // a client may sensibly retry.
            case TEMPORARILY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            // Authenticated, and refused or interrupted — with or without a next step.
            case DENY, CONTEXT_REQUIRED, AUTHORITY_REQUIRED, CONSENT_REQUIRED, APPROVAL_REQUIRED,
                 BREAK_GLASS_AVAILABLE, RECOVERY_REQUIRED -> HttpStatus.FORBIDDEN;
        };
    }
}
