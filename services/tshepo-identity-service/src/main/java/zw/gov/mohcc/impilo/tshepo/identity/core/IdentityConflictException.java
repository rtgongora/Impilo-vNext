package zw.gov.mohcc.impilo.tshepo.identity.core;

/**
 * Thrown when a mapping or link already exists and the operation would create a conflict.
 */
public class IdentityConflictException extends RuntimeException {

    public IdentityConflictException(String message) {
        super(message);
    }
}
