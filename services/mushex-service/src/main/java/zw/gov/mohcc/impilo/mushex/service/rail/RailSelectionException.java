package zw.gov.mohcc.impilo.mushex.service.rail;

import zw.gov.mohcc.impilo.mushex.domain.enums.RailSelectionReason;

/**
 * Thrown by {@link RailSelectionPolicy} when no valid rail can be chosen for the
 * request. The {@link #getErrorCode()} value is a stable identifier suitable for the
 * outer {@code ApiResponse.error(...)} envelope; see
 * {@code docs/design/g4-rail-selection-policy.md} §10.
 */
public class RailSelectionException extends IllegalArgumentException {

    private final String errorCode;
    private final RailSelectionReason reason;

    public RailSelectionException(String errorCode, RailSelectionReason reason, String message) {
        super(message);
        this.errorCode = errorCode;
        this.reason = reason;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public RailSelectionReason getReason() {
        return reason;
    }
}
