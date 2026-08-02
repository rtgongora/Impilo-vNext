package zw.gov.mohcc.impilo.experience.trust;

import org.springframework.web.server.ResponseStatusException;
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
 *
 * <h2>Why it extends {@link ResponseStatusException}</h2>
 *
 * <p>Not cosmetic.</p>
 *
 * <p>The BFF's controllers wrap governance calls in {@code catch (ResponseStatusException)}
 * followed by {@code catch (Exception)} — TeleconsultController alone has 53 catch-alls around 39
 * governance calls. The refusals this replaces WERE ResponseStatusExceptions, so a plain
 * RuntimeException fell straight through to the catch-all and came back as
 * {@code 502 PCT_UNAVAILABLE}: a governance refusal reported to the user as a downstream service
 * outage. That was measured on a live pod, not reasoned about.</p>
 *
 * <p>Extending it means every existing catch produces the same sensible status it always did, and
 * any path that lets the exception propagate additionally gets the full challenge envelope —
 * strictly better everywhere, worse nowhere.</p>
 *
 * The reason carried to those catch blocks is the user message KEY, never the log message --
 * several of them echo `getReason()` straight to the client, and "Tshepo PDP denied telemedicine
 * <p>The reason carried to those catch blocks is the user message KEY, never the log message —
 * several of them echo {@code getReason()} straight to the client, and "Tshepo PDP denied
 * telemedicine read" names our internal topology while telling the user nothing actionable.</p>
 */
public class TrustChallengeException extends ResponseStatusException {

    private final transient TrustChallengeOutcome outcome;
    private final String logMessage;

    public TrustChallengeException(TrustChallengeOutcome outcome, String logMessage) {
        super(TrustChallengeResponder.statusFor(outcome == null ? null : outcome.decision()),
                outcome == null || outcome.userMessageKey() == null
                        ? "trust.deny.generic" : outcome.userMessageKey());
        this.outcome = outcome;
        this.logMessage = logMessage;
    }

    /**
     * The internal description, for logs only — never the text sent to a client.
     *
     * <p>Held in its own field because {@code ResponseStatusException.getMessage()} returns the
     * formatted status line, so delegating there would silently discard what the caller passed.</p>
     */
    public String logMessage() {
        return logMessage;
    }

    public TrustChallengeOutcome outcome() {
        return outcome;
    }
}
