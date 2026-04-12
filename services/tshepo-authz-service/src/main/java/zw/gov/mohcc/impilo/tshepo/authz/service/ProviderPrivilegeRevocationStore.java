package zw.gov.mohcc.impilo.tshepo.authz.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed marker that a VARAPI provider public id is suspended or revoked.
 *
 * <p>Legacy VARAPI Kafka payloads do not always include tenant id; provider
 * public ids are treated as the stable correlation surface for ext_authz.</p>
 */
@Service
public class ProviderPrivilegeRevocationStore {

    private static final Logger log = LoggerFactory.getLogger(ProviderPrivilegeRevocationStore.class);
    private static final String KEY_PREFIX = "tshepo:authz:provider-privilege:";
    private static final String MARKER = "REVOKED";
    private static final Duration DEFAULT_TTL = Duration.ofDays(30);

    private final RedisTemplate<String, Object> redisTemplate;

    public ProviderPrivilegeRevocationStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void markRevoked(String providerPublicId) {
        markRevoked(providerPublicId, DEFAULT_TTL);
    }

    public void markRevoked(String providerPublicId, Duration ttl) {
        if (providerPublicId == null || providerPublicId.isBlank()) {
            return;
        }
        String key = KEY_PREFIX + providerPublicId.trim();
        try {
            redisTemplate.opsForValue().set(key, MARKER, ttl);
            log.debug("Marked provider privilege revoked in Redis: key={}", key);
        } catch (Exception e) {
            log.warn("Redis write failed for provider privilege revocation ({}): {}", key, e.getMessage());
        }
    }

    public void clearRevoked(String providerPublicId) {
        if (providerPublicId == null || providerPublicId.isBlank()) {
            return;
        }
        String key = KEY_PREFIX + providerPublicId.trim();
        try {
            redisTemplate.delete(key);
            log.debug("Cleared provider privilege revocation in Redis: key={}", key);
        } catch (Exception e) {
            log.warn("Redis delete failed for provider privilege revocation ({}): {}", key, e.getMessage());
        }
    }

    public boolean isRevoked(String providerPublicId) {
        if (providerPublicId == null || providerPublicId.isBlank()) {
            return false;
        }
        String key = KEY_PREFIX + providerPublicId.trim();
        try {
            Object v = redisTemplate.opsForValue().get(key);
            return v != null && MARKER.equals(String.valueOf(v));
        } catch (Exception e) {
            log.warn("Redis read failed for provider privilege revocation ({}): {}", key, e.getMessage());
            return false;
        }
    }
}
