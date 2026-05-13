package zw.gov.mohcc.impilo.mushex.service.rail;

/**
 * Thrown by {@link RailEnforcement} when the rail resolved from intent metadata
 * cannot be used at attempt time — typically because the named adapter is no
 * longer registered, or because the metadata carries an unknown rail.
 *
 * <p>The {@link #getErrorCode()} value is a stable identifier suitable for the
 * outer {@code ApiResponse.error(...)} envelope; see
 * {@code docs/design/phase-3-attempt-time-rail-enforcement-implementation.md} §3.4.
 */
public class RailEnforcementException extends IllegalStateException {

    public static final String CODE_RAIL_UNAVAILABLE = "RAIL_ADAPTER_UNAVAILABLE_AT_ATTEMPT";

    private final String errorCode;

    public RailEnforcementException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
