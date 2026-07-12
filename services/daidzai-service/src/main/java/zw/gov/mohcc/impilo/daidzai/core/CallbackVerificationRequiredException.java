package zw.gov.mohcc.impilo.daidzai.core;

/**
 * Raised when a request captured as public-anonymous (status {@code AWAITING_CALLBACK}) is asked
 * to triage/dispatch before a dispatcher or responder has reached the caller on the callback
 * number. This is the PD-3 dispatch gate: the request is real and captured immediately, but no
 * resource moves until callback verification (see {@code EmergencyService.verifyCallback}).
 *
 * <p>Mapped by {@link zw.gov.mohcc.impilo.daidzai.api.DaidzaiExceptionHandler} to HTTP 409.</p>
 */
public class CallbackVerificationRequiredException extends RuntimeException {
    public CallbackVerificationRequiredException(String message) {
        super(message);
    }
}
