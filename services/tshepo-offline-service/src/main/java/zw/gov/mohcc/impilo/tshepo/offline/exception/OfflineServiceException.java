package zw.gov.mohcc.impilo.tshepo.offline.exception;

/**
 * Base exception for all TSHEPO Offline service errors.
 */
public class OfflineServiceException extends RuntimeException {

    private final String errorCode;

    public OfflineServiceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public OfflineServiceException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
