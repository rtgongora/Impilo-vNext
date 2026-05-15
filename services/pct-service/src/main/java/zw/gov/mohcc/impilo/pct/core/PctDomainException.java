package zw.gov.mohcc.impilo.pct.core;

/**
 * Typed domain exception for API-safe error codes and statuses.
 */
public class PctDomainException extends RuntimeException {
    private final String code;
    private final int status;

    public PctDomainException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }
}
