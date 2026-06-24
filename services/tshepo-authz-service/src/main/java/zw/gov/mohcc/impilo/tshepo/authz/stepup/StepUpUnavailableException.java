package zw.gov.mohcc.impilo.tshepo.authz.stepup;

/**
 * Thrown when a step-up verification mode cannot be honoured because its provider
 * is not configured (e.g. no SMS delivery, no biometric matcher, no enrolled TOTP
 * secret). Step-up MUST fail closed in this case — it never silently succeeds.
 */
public class StepUpUnavailableException extends RuntimeException {
    public StepUpUnavailableException(String message) {
        super(message);
    }
}
