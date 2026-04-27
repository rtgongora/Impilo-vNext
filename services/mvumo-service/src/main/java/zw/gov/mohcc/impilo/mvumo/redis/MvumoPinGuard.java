package zw.gov.mohcc.impilo.mvumo.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Brute-force protection for PIN/OTP style challenges: count failures, optional lockout window.
 * Keys: {@value MvumoRedisKeySupport#PIN_FAIL}{@code {guardKey}} and {@value MvumoRedisKeySupport#PIN_LOCK}{@code {guardKey}}.
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class MvumoPinGuard {

    private final StringRedisTemplate redis;
    private final int maxFailures;
    private final int lockoutSeconds;
    private final int failTtlSeconds;

    public MvumoPinGuard(
            StringRedisTemplate redis,
            @Value("${mvumo.pin-guard.max-failures:5}") int maxFailures,
            @Value("${mvumo.pin-guard.lockout-seconds:900}") int lockoutSeconds,
            @Value("${mvumo.pin-guard.fail-counter-ttl-seconds:3600}") int failTtlSeconds) {
        this.redis = redis;
        this.maxFailures = maxFailures;
        this.lockoutSeconds = lockoutSeconds;
        this.failTtlSeconds = failTtlSeconds;
    }

    public boolean isLockedOut(String guardKey) {
        if (guardKey == null || guardKey.isBlank()) {
            return false;
        }
        return "1".equals(redis.opsForValue().get(MvumoRedisKeySupport.PIN_LOCK + guardKey));
    }

    /**
     * Call after a failed verification. Returns true if a lockout was applied.
     */
    public boolean recordFailure(String guardKey) {
        if (guardKey == null || guardKey.isBlank()) {
            return false;
        }
        String fKey = MvumoRedisKeySupport.PIN_FAIL + guardKey;
        Long n = redis.opsForValue().increment(fKey);
        if (n != null && n == 1) {
            redis.expire(fKey, Duration.ofSeconds(Math.max(60, failTtlSeconds)));
        }
        if (n != null && n >= maxFailures) {
            String lKey = MvumoRedisKeySupport.PIN_LOCK + guardKey;
            redis.opsForValue().set(lKey, "1", lockoutSeconds, TimeUnit.SECONDS);
            redis.delete(fKey);
            return true;
        }
        return false;
    }

    public void clear(String guardKey) {
        if (guardKey == null) {
            return;
        }
        redis.delete(MvumoRedisKeySupport.PIN_FAIL + guardKey);
        redis.delete(MvumoRedisKeySupport.PIN_LOCK + guardKey);
    }
}
