package zw.gov.mohcc.impilo.msikaflow.core;

/**
 * A required upstream (e.g. share-slip-service) could not be reached.
 * Mapped to HTTP 502 by the GlobalExceptionHandler — never faked as success.
 */
public class UpstreamUnavailableException extends RuntimeException {

    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public UpstreamUnavailableException(String message) {
        super(message);
    }
}
