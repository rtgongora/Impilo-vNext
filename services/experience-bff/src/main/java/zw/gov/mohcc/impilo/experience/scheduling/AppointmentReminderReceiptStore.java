package zw.gov.mohcc.impilo.experience.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable reminder dedup — Redis when available, in-memory fallback for dev/tests.
 */
@Component
public class AppointmentReminderReceiptStore {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderReceiptStore.class);
    private static final String KEY_PREFIX = "impilo:appointment:reminder:";
    private static final Duration TTL = Duration.ofDays(14);

    private final StringRedisTemplate redis;
    private final Set<String> localFallback = ConcurrentHashMap.newKeySet();

    public AppointmentReminderReceiptStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryClaim(String dedupKey) {
        if (dedupKey == null || dedupKey.isBlank()) {
            return false;
        }
        try {
            Boolean claimed = redis.opsForValue().setIfAbsent(KEY_PREFIX + dedupKey, "1", TTL);
            return Boolean.TRUE.equals(claimed);
        } catch (Exception ex) {
            log.debug("Redis reminder dedup unavailable, using local fallback: {}", ex.getMessage());
            return localFallback.add(dedupKey);
        }
    }
}
