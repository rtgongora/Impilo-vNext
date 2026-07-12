package zw.gov.mohcc.impilo.msikaflow.core;

/**
 * Business-rule validation failure carrying structured per-item details.
 * Mapped to HTTP 422 by the GlobalExceptionHandler; state is never mutated
 * before this is thrown.
 */
public class ValidationFailedException extends RuntimeException {

    private final String code;
    private final transient Object details;

    public ValidationFailedException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public Object getDetails() {
        return details;
    }
}
