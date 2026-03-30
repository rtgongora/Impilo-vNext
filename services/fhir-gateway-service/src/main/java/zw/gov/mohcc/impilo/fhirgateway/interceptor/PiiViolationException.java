package zw.gov.mohcc.impilo.fhirgateway.interceptor;

public class PiiViolationException extends RuntimeException {

    public PiiViolationException(String message) {
        super(message);
    }
}
