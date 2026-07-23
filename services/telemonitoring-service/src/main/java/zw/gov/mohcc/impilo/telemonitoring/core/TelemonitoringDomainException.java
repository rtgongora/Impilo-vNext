package zw.gov.mohcc.impilo.telemonitoring.core;

/**
 * Typed domain exception carrying an API-safe error code + HTTP status, so telemonitoring
 * domain rejections surface as coded envelopes (not opaque 500s or raw Spring error
 * bodies). Mirrors the OROS/PCT {@code *DomainException} pattern: the BFF forwards the
 * real status + code instead of masking a governed rejection as a fake outage.
 */
public class TelemonitoringDomainException extends RuntimeException {

    private final String code;
    private final int status;

    public TelemonitoringDomainException(String code, int status, String message) {
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
