package zw.gov.mohcc.impilo.tshepo.authz.session;

/**
 * Thrown when a bearer token fails session validation.
 */
public class SessionValidationException extends RuntimeException {

    private final String errorCode;

    public SessionValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SessionValidationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
