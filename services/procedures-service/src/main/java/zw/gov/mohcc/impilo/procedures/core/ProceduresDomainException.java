package zw.gov.mohcc.impilo.procedures.core;

/**
 * Typed domain exception carrying an API-safe error code and HTTP status, so a governed
 * rejection surfaces as a coded envelope rather than an opaque 500. Mirrors the OROS/PCT
 * {@code *DomainException} pattern: callers forward the real status and code instead of
 * masking a deliberate refusal as an outage — which matters more here than elsewhere,
 * because a caller that cannot tell "not ready" from "could not tell" will render the
 * second as the first.
 */
public class ProceduresDomainException extends RuntimeException {

    private final String code;
    private final int status;

    public ProceduresDomainException(String code, int status, String message) {
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
