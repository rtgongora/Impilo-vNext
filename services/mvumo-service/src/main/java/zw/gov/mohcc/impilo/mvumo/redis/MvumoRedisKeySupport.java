package zw.gov.mohcc.impilo.mvumo.redis;

/**
 * Key prefixes for Redis use in Mvumo (observability, rate limits, challenge state).
 * Keep namespace {@code mvumo:*} distinct from other services.
 */
public final class MvumoRedisKeySupport {

    public static final String REMOTE_TOKEN = "mvumo:rt:";
    /** Fixed-window or sliding count for a logical scope (IP, user, or session). */
    public static final String RATE = "mvumo:rl:";
    /** Per-key failure count (PIN/OTP) before lockout. */
    public static final String PIN_FAIL = "mvumo:pin:fail:";
    /** Optional lockout marker with TTL. */
    public static final String PIN_LOCK = "mvumo:pin:lock:";

    private MvumoRedisKeySupport() {}
}
