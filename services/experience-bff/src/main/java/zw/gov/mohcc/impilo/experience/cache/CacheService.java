package zw.gov.mohcc.impilo.experience.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Redis cache-aside service for hot-path read caching.
 *
 * <p>Short TTL per domain ensures data freshness. Kafka-driven eviction
 * via {@link CacheEvictionConsumer} removes stale entries when sovereign
 * services publish change events.</p>
 *
 * <p>This is a performance optimization, NOT a fallback. If Redis is empty,
 * the BFF calls the sovereign service synchronously.</p>
 */
@Component
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Get from cache or fetch from sovereign service.
     *
     * @param cacheKey unique cache key (e.g. "patient:cpid-123:summary")
     * @param ttl      time-to-live for this entry
     * @param fetcher  supplier that calls the sovereign service on cache miss
     * @return the cached or freshly-fetched data
     */
    public JsonNode getOrFetch(String cacheKey, Duration ttl, Supplier<JsonNode> fetcher) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.trace("Cache HIT: {}", cacheKey);
                return objectMapper.readTree(cached);
            }
        } catch (Exception e) {
            log.debug("Redis read failed for {} — falling through to service call", cacheKey);
        }

        log.trace("Cache MISS: {}", cacheKey);
        JsonNode result = fetcher.get();

        try {
            if (result != null) {
                String json = objectMapper.writeValueAsString(result);
                redisTemplate.opsForValue().set(cacheKey, json, ttl);
            }
        } catch (Exception e) {
            log.debug("Redis write failed for {} — result still returned", cacheKey);
        }

        return result;
    }

    /**
     * Evict a single cache entry.
     */
    public void evict(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
            log.trace("Cache EVICT: {}", cacheKey);
        } catch (Exception e) {
            log.debug("Redis evict failed for {}", cacheKey);
        }
    }

    /**
     * Evict all entries matching a pattern (e.g. "patient:cpid-123:*").
     */
    public void evictPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.trace("Cache EVICT pattern: {} ({} keys)", pattern, keys.size());
            }
        } catch (Exception e) {
            log.debug("Redis pattern evict failed for {}", pattern);
        }
    }
}
