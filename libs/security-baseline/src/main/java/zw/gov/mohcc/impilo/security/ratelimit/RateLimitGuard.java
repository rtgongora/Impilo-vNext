package zw.gov.mohcc.impilo.security.ratelimit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Token-bucket rate limiter for service-level request throttling.
 * <p>
 * Default storage is in-memory ({@link ConcurrentHashMap}). For distributed
 * deployments, supply a {@link RateLimitStore} implementation backed by
 * Redis or JDBC.
 * <p>
 * Thread-safe. Each bucket is keyed by an opaque string (actor ID, IP, tenant, etc.).
 */
public final class RateLimitGuard {

    private final long maxTokens;
    private final long refillPerSecond;
    private final RateLimitStore store;

    /**
     * @param maxTokens       bucket capacity
     * @param refillPerSecond  tokens added per second
     */
    public RateLimitGuard(long maxTokens, long refillPerSecond) {
        this(maxTokens, refillPerSecond, new InMemoryRateLimitStore());
    }

    public RateLimitGuard(long maxTokens, long refillPerSecond, RateLimitStore store) {
        if (maxTokens < 1) throw new IllegalArgumentException("maxTokens must be >= 1");
        if (refillPerSecond < 1) throw new IllegalArgumentException("refillPerSecond must be >= 1");
        this.maxTokens = maxTokens;
        this.refillPerSecond = refillPerSecond;
        this.store = Objects.requireNonNull(store);
    }

    /**
     * Attempt to consume one token for the given key.
     *
     * @param key bucket key (e.g. actor ID, IP address, tenant+endpoint)
     * @return a {@link RateLimitResult} indicating allow/deny and remaining tokens
     */
    public RateLimitResult tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    /**
     * Attempt to consume {@code tokens} from the bucket for the given key.
     */
    public RateLimitResult tryAcquire(String key, int tokens) {
        Objects.requireNonNull(key, "rate limit key must not be null");
        if (tokens < 1) throw new IllegalArgumentException("tokens must be >= 1");

        Bucket bucket = store.getOrCreate(key, maxTokens);
        long nowNanos = System.nanoTime();

        synchronized (bucket) {
            // Refill tokens based on elapsed time
            long elapsedNanos = nowNanos - bucket.lastRefillNanos();
            if (elapsedNanos > 0) {
                long tokensToAdd = (elapsedNanos * refillPerSecond) / 1_000_000_000L;
                if (tokensToAdd > 0) {
                    long newTokens = Math.min(maxTokens, bucket.tokens() + tokensToAdd);
                    bucket.setTokens(newTokens);
                    bucket.setLastRefillNanos(nowNanos);
                }
            }

            if (bucket.tokens() >= tokens) {
                bucket.setTokens(bucket.tokens() - tokens);
                long retryAfterSeconds = 0;
                return new RateLimitResult(true, bucket.tokens(), maxTokens, retryAfterSeconds);
            } else {
                // Calculate when enough tokens will be available
                long deficit = tokens - bucket.tokens();
                long retryAfterSeconds = Math.max(1, (deficit + refillPerSecond - 1) / refillPerSecond);
                return new RateLimitResult(false, bucket.tokens(), maxTokens, retryAfterSeconds);
            }
        }
    }

    public long getMaxTokens() { return maxTokens; }
    public long getRefillPerSecond() { return refillPerSecond; }

    // ================================================================
    // Bucket data structure
    // ================================================================

    public static final class Bucket {
        private long tokens;
        private long lastRefillNanos;

        public Bucket(long tokens, long lastRefillNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = lastRefillNanos;
        }

        public long tokens() { return tokens; }
        public void setTokens(long tokens) { this.tokens = tokens; }
        public long lastRefillNanos() { return lastRefillNanos; }
        public void setLastRefillNanos(long lastRefillNanos) { this.lastRefillNanos = lastRefillNanos; }
    }

    // ================================================================
    // Store contract + in-memory default
    // ================================================================

    /**
     * Pluggable store for rate-limit buckets.
     * Default: in-memory. Override for Redis/JDBC in production.
     */
    public interface RateLimitStore {
        Bucket getOrCreate(String key, long maxTokens);
    }

    static final class InMemoryRateLimitStore implements RateLimitStore {
        private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

        @Override
        public Bucket getOrCreate(String key, long maxTokens) {
            return buckets.computeIfAbsent(key,
                    k -> new Bucket(maxTokens, System.nanoTime()));
        }
    }
}
