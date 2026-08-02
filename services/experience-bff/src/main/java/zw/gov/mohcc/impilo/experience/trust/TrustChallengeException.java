package zw.gov.mohcc.impilo.experience.trust;

import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeOutcome;

/**
 * A refusal that still carries the PDP's reasoning.
 *
 * <p>Every governance service in this module refuses with
 * {@code new ResponseStatusException(FORBIDDEN, "Tshepo PDP denied telemedicine read")} — a bare
 * status and an English sentence written for a log, not a person. The shell receives a 403 with no
 * reason code, no permitted next step and no continuation, which is why the only challenge UI in
 * the whole product is the hardcoded step-up prompt.</p>
 *
 * <p>Carrying the canonical {@link TrustChallengeOutcome} instead means the same refusal can be
 * rendered as "this needs the patient's consent" with a way to obtain it, rather than as a dead
 * end — without any governance service learning how to build an HTTP response.</p>
 */
public class TrustChallengeException extends RuntimeException {

    private final transient TrustChallengeOutcome outcome;

    public TrustChallengeException(TrustChallengeOutcome outcome, String logMessage) {
        super(logMessage);
        this.outcome = outcome;
    }

    public TrustChallengeOutcome outcome() {
        return outcome;
    }
}
