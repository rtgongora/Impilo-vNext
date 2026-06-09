package zw.gov.mohcc.impilo.live.core;

import org.springframework.http.HttpStatus;

/**
 * Raised when a {@code LiveEvent} operation violates Impilo Live governance doctrine (§8):
 * free-floating clinical sessions, missing consent, unmoderated public broadcasts, unauthorised
 * access to restricted sessions, recordings without policy, etc.
 */
public class LiveGovernanceException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public LiveGovernanceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static LiveGovernanceException invalid(String code, String message) {
        return new LiveGovernanceException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static LiveGovernanceException forbidden(String code, String message) {
        return new LiveGovernanceException(HttpStatus.FORBIDDEN, code, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
