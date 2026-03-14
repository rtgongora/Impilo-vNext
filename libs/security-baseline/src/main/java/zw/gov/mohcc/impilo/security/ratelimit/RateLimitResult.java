package zw.gov.mohcc.impilo.security.ratelimit;

/**
 * Result of a rate-limit check.
 *
 * @param allowed          whether the request is permitted
 * @param remainingTokens  tokens remaining in the bucket after this check
 * @param limit            maximum bucket capacity
 * @param retryAfterSeconds seconds until enough tokens are available (0 if allowed)
 */
public record RateLimitResult(
        boolean allowed,
        long remainingTokens,
        long limit,
        long retryAfterSeconds
) {
    /** Standard rate-limit response headers. */
    public static final String HEADER_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    public static final String HEADER_RETRY_AFTER = "Retry-After";
}
