package zw.gov.mohcc.impilo.tshepo.offline.core;

/**
 * Thrown when the JWKS cannot be obtained: a fetch failed and there is no last-known-good
 * cached key set to fall back on. Verification fails closed (JWKS_UNAVAILABLE) in this case.
 */
public class JwksUnavailableException extends RuntimeException {
    public JwksUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
