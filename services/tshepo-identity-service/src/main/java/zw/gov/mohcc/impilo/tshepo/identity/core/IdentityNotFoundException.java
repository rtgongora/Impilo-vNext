package zw.gov.mohcc.impilo.tshepo.identity.core;

/**
 * Thrown when an identity resolution or mapping lookup fails.
 *
 * <p>Returns a generic 404 with no enumeration hints — the response message
 * never reveals whether the identifier exists in another tenant or context.</p>
 */
public class IdentityNotFoundException extends RuntimeException {

    public IdentityNotFoundException(String message) {
        super(message);
    }
}
