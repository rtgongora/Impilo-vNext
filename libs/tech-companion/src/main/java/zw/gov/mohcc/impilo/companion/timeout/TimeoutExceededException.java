package zw.gov.mohcc.impilo.companion.timeout;

/**
 * Thrown when a client-specified timeout is exceeded.
 *
 * Caught by controllers / exception handlers to produce
 * a v1.1 error envelope with code CLIENT_TIMEOUT_EXCEEDED.
 */
public class TimeoutExceededException extends RuntimeException {

    private final long timeoutMs;

    public TimeoutExceededException(long timeoutMs) {
        super("Client timeout exceeded: " + timeoutMs + "ms");
        this.timeoutMs = timeoutMs;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }
}
