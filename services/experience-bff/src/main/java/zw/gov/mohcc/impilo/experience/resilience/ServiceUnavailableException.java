package zw.gov.mohcc.impilo.experience.resilience;

/**
 * Thrown when a sovereign service is unavailable — circuit open, bulkhead full,
 * or call failed after retry. The BFF returns HTTP 503 with structured error.
 */
public class ServiceUnavailableException extends RuntimeException {

    private final String serviceName;
    private final String reason;

    public ServiceUnavailableException(String serviceName, String reason) {
        super("Service unavailable: " + serviceName + " — " + reason);
        this.serviceName = serviceName;
        this.reason = reason;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getReason() {
        return reason;
    }
}
