package zw.gov.mohcc.impilo.dispatch.core;

public class InvalidStatusTransitionException extends RuntimeException {

    private final String currentStatus;
    private final String requestedStatus;

    public InvalidStatusTransitionException(String currentStatus, String requestedStatus) {
        super("Invalid status transition from " + currentStatus + " to " + requestedStatus);
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public String getCurrentStatus() { return currentStatus; }
    public String getRequestedStatus() { return requestedStatus; }
}
