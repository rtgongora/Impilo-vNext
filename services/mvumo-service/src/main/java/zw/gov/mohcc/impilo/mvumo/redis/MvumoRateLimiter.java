package zw.gov.mohcc.impilo.mvumo.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Token-bucket style limit using a fixed window: INCR + EXPIRE on first hit.
 * Keys: {@value MvumoRedisKeySupport#RATE}{@code {scope}}.
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class MvumoRateLimiter {

    private final StringRedisTemplate redis;
    private final int defaultMax;
    private final int defaultWindowSec;

    public MvumoRateLimiter(
            StringRedisTemplate redis,
            @Value("${mvumo.redis-rate-limit.max-per-window:60}") int defaultMax,
            @Value("${mvumo.redis-rate-limit.window-seconds:60}") int defaultWindowSec) {
        this.redis = redis;
        this.defaultMax = defaultMax;
        this.defaultWindowSec = defaultWindowSec;
    }

    /**
     * @return true if this request is allowed, false if the window is full.
     */
    public boolean tryAcquire(String safeScope) {
        return tryAcquire(safeScope, defaultMax, defaultWindowSec);
    }

    public boolean tryAcquire(String safeScope, int maxPerWindow, int windowSeconds) {
        if (safeScope == null || safeScope.isBlank()) {
            return true;
        }
        String key = MvumoRedisKeySupport.RATE + safeScope;
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1) {
            redis.expire(key, Duration.ofSeconds(Math.max(1, windowSeconds)));
        }
        return n != null && n <= maxPerWindow;
    }

    public long getCurrentCount(String safeScope) {
        String v = redis.opsForValue().get(MvumoRedisKeySupport.RATE + safeScope);
        if (v == null) {
            return 0;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void reset(String safeScope) {
        redis.delete(MvumoRedisKeySupport.RATE + safeScope);
    }
}
