package zw.gov.mohcc.impilo.companion.federation;

import zw.gov.mohcc.impilo.companion.error.ErrorCodes;

/**
 * Thrown when a non-national pod attempts a national-authoritative operation.
 *
 * Caught by controllers / exception handlers to produce a v1.1 error envelope
 * with code FEDERATION_AUTHORITY_VIOLATION and HTTP 403.
 */
public class FederationAuthorityViolationException extends RuntimeException {

    private final String podId;

    public FederationAuthorityViolationException(String podId, String message) {
        super(message);
        this.podId = podId;
    }

    public String getPodId() {
        return podId;
    }

    public String getErrorCode() {
        return ErrorCodes.FEDERATION_AUTHORITY_VIOLATION;
    }
}
