package zw.gov.mohcc.impilo.oros.core;

/**
 * Typed domain exception carrying an API-safe error code + HTTP status, so OROS
 * domain rejections surface as coded envelopes (not opaque 500s or raw Spring
 * error bodies). Mirrors the PCT {@code PctDomainException} pattern established by
 * the A0 error-passthrough work: the BFF forwards the real status + code instead of
 * masking a governed rejection as a fake outage.
 */
public class OrosDomainException extends RuntimeException {
    private final String code;
    private final int status;

    public OrosDomainException(String code, int status, String message) {
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
