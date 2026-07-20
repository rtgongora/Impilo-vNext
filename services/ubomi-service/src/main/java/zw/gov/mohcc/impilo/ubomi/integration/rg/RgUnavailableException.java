package zw.gov.mohcc.impilo.ubomi.integration.rg;

/**
 * Typed signal that the Registrar-General could not be reached or did not answer
 * in time (timeout, connection error, 5xx). This is the fail-open-for-care
 * boundary: callers catch it and park the attempt as {@code RG_PENDING} for the
 * reconcile loop — an RG outage NEVER blocks registration or care.
 *
 * <p>Distinct from an authoritative non-match, which is a normal
 * {@link RgVerificationOutcome} with {@code matched=false} and is <em>not</em>
 * an exception.</p>
 */
public class RgUnavailableException extends RuntimeException {
    public RgUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
    public RgUnavailableException(String message) {
        super(message);
    }
}
