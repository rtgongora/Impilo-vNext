package zw.gov.mohcc.impilo.nhume.core;

public class InvalidDeliveryTransitionException extends RuntimeException {
    private final String currentStatus;
    private final String requestedStatus;

    public InvalidDeliveryTransitionException(String currentStatus, String requestedStatus) {
        super(String.format("Invalid delivery transition: %s -> %s", currentStatus, requestedStatus));
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public String getCurrentStatus() { return currentStatus; }
    public String getRequestedStatus() { return requestedStatus; }
}
