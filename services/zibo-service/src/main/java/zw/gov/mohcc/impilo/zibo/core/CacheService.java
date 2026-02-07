package zw.gov.mohcc.impilo.zibo.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.zibo.config.ZiboProperties;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-based caching service for hot terminology lookups.
 *
 * <p>Caches frequently accessed artifact content and mapping results to
 * reduce database load. Uses a configurable TTL from
 * {@link ZiboProperties.Cache#getTtlMinutes()}.</p>
 *
 * <p>Gracefully degrades when Redis is unavailable: all cache operations
 * log a warning and return empty/no-op results, allowing the system to
 * continue operating against the database.</p>
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private static final String ARTIFACT_KEY_PREFIX = "zibo:artifact:";
    private static final String MAPPING_KEY_PREFIX = "zibo:mapping:";

    private final StringRedisTemplate redisTemplate;
    private final ZiboProperties ziboProperties;
    private final boolean redisAvailable;

    /**
     * Construct the cache service.
     *
     * <p>The {@code StringRedisTemplate} parameter is optional; if Spring
     * cannot autowire it (e.g. no Redis configured), the service degrades
     * gracefully.</p>
     *
     * @param redisTemplate the Redis template; may be {@code null} if Redis is unavailable
     * @param ziboProperties the ZIBO configuration properties
     */
    public CacheService(StringRedisTemplate redisTemplate,
                        ZiboProperties ziboProperties) {
        this.ziboProperties = ziboProperties;

        boolean available = false;
        if (redisTemplate != null) {
            try {
                redisTemplate.getConnectionFactory().getConnection().ping();
                available = true;
            } catch (Exception e) {
                log.warn("Redis is not available, caching will be bypassed: {}", e.getMessage());
            }
        }

        this.redisTemplate = redisTemplate;
        this.redisAvailable = available;
    }

    /**
     * Cache an artifact's content JSON by its canonical URL and version.
     *
     * @param canonicalUrl the artifact's canonical URL
     * @param version      the artifact version
     * @param contentJson  the content to cache
     */
    public void cacheArtifact(String canonicalUrl, String version, String contentJson) {
        if (!redisAvailable) {
            return;
        }
        try {
            String key = buildArtifactKey(canonicalUrl, version);
            Duration ttl = Duration.ofMinutes(ziboProperties.getCache().getTtlMinutes());
            redisTemplate.opsForValue().set(key, contentJson, ttl);
            log.debug("Cached artifact: url={}, version={}", canonicalUrl, version);
        } catch (Exception e) {
            log.warn("Failed to cache artifact [{}|{}]: {}", canonicalUrl, version, e.getMessage());
        }
    }

    /**
     * Retrieve a cached artifact's content JSON.
     *
     * @param canonicalUrl the artifact's canonical URL
     * @param version      the artifact version
     * @return the cached content, or empty if not cached or Redis unavailable
     */
    public Optional<String> getCachedArtifact(String canonicalUrl, String version) {
        if (!redisAvailable) {
            return Optional.empty();
        }
        try {
            String key = buildArtifactKey(canonicalUrl, version);
            String value = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.warn("Failed to retrieve cached artifact [{}|{}]: {}", canonicalUrl, version, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Invalidate a cached artifact entry.
     *
     * @param canonicalUrl the artifact's canonical URL
     * @param version      the artifact version
     */
    public void invalidateArtifact(String canonicalUrl, String version) {
        if (!redisAvailable) {
            return;
        }
        try {
            String key = buildArtifactKey(canonicalUrl, version);
            redisTemplate.delete(key);
            log.debug("Invalidated cached artifact: url={}, version={}", canonicalUrl, version);
        } catch (Exception e) {
            log.warn("Failed to invalidate cached artifact [{}|{}]: {}", canonicalUrl, version, e.getMessage());
        }
    }

    /**
     * Cache a mapping result.
     *
     * @param key    the mapping cache key (e.g. sourceSystem|sourceCode|targetSystem)
     * @param result the serialized mapping result
     */
    public void cacheMapping(String key, String result) {
        if (!redisAvailable) {
            return;
        }
        try {
            String redisKey = MAPPING_KEY_PREFIX + key;
            Duration ttl = Duration.ofMinutes(ziboProperties.getCache().getTtlMinutes());
            redisTemplate.opsForValue().set(redisKey, result, ttl);
            log.debug("Cached mapping: key={}", key);
        } catch (Exception e) {
            log.warn("Failed to cache mapping [{}]: {}", key, e.getMessage());
        }
    }

    /**
     * Retrieve a cached mapping result.
     *
     * @param key the mapping cache key
     * @return the cached result, or empty if not cached or Redis unavailable
     */
    public Optional<String> getCachedMapping(String key) {
        if (!redisAvailable) {
            return Optional.empty();
        }
        try {
            String redisKey = MAPPING_KEY_PREFIX + key;
            String value = redisTemplate.opsForValue().get(redisKey);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.warn("Failed to retrieve cached mapping [{}]: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private String buildArtifactKey(String canonicalUrl, String version) {
        return ARTIFACT_KEY_PREFIX + canonicalUrl + "|" + version;
    }
}
