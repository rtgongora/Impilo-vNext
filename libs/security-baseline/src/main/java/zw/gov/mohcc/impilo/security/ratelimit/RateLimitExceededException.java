package zw.gov.mohcc.impilo.security.ratelimit;

/**
 * Thrown when a rate-limit check fails. Carries the result so the caller
 * can set appropriate response headers (Retry-After, X-RateLimit-*).
 */
public class RateLimitExceededException extends RuntimeException {

    private final RateLimitResult result;

    public RateLimitExceededException(RateLimitResult result) {
        super("Rate limit exceeded. Retry after " + result.retryAfterSeconds() + "s");
        this.result = result;
    }

    public RateLimitResult getResult() { return result; }
}
