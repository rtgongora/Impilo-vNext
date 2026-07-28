package zw.gov.mohcc.impilo.surgery.core;

/**
 * Typed domain exception carrying an API-safe error code and HTTP status, mirroring
 * {@code procedures-service}'s own {@code ProceduresDomainException} — a caller forwards the
 * real status and code instead of masking a deliberate refusal as an outage.
 */
public class SurgeryDomainException extends RuntimeException {

    private final String code;
    private final int status;

    public SurgeryDomainException(String code, int status, String message) {
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
