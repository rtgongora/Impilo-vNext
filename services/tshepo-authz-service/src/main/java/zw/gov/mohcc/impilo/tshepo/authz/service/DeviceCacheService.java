package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.DeviceProfileEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.DeviceProfileRepository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed cache for device profiles.
 *
 * <p>Device risk scoring happens on every request, so device profiles
 * are cached in Redis with a configurable TTL (default 600s).</p>
 */
@Service
public class DeviceCacheService {

    private static final Logger log = LoggerFactory.getLogger(DeviceCacheService.class);
    private static final String CACHE_KEY_PREFIX = "tshepo:authz:device:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DeviceProfileRepository deviceRepository;
    private final AuthzProperties properties;
    private final ObjectMapper objectMapper;

    public DeviceCacheService(RedisTemplate<String, Object> redisTemplate,
                              DeviceProfileRepository deviceRepository,
                              AuthzProperties properties,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.deviceRepository = deviceRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Get device profile by tenant + fingerprint. Tries Redis, falls back to DB.
     */
    public Optional<DeviceProfileEntity> getDeviceProfile(UUID tenantId, String fingerprint) {
        String cacheKey = CACHE_KEY_PREFIX + tenantId + ":" + fingerprint;

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Device profile cache HIT: tenant={}, fingerprint={}", tenantId, fingerprint);
                return Optional.of(objectMapper.convertValue(cached, DeviceProfileEntity.class));
            }
        } catch (Exception e) {
            log.warn("Redis read failed for device profile, falling back to DB: {}", e.getMessage());
        }

        log.debug("Device profile cache MISS: tenant={}, fingerprint={}", tenantId, fingerprint);
        Optional<DeviceProfileEntity> device = deviceRepository.findByTenantIdAndFingerprint(tenantId, fingerprint);

        device.ifPresent(d -> cacheDeviceProfile(tenantId, fingerprint, d));

        return device;
    }

    /**
     * Cache a device profile after creation or update.
     */
    public void cacheDeviceProfile(UUID tenantId, String fingerprint, DeviceProfileEntity device) {
        String cacheKey = CACHE_KEY_PREFIX + tenantId + ":" + fingerprint;
        try {
            redisTemplate.opsForValue().set(cacheKey, device,
                    Duration.ofSeconds(properties.getCache().getDeviceProfileTtlSeconds()));
        } catch (Exception e) {
            log.warn("Redis write failed for device profile: {}", e.getMessage());
        }
    }

    /**
     * Invalidate cache for a specific device.
     */
    public void invalidate(UUID tenantId, String fingerprint) {
        String cacheKey = CACHE_KEY_PREFIX + tenantId + ":" + fingerprint;
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("Redis delete failed for device profile cache: {}", e.getMessage());
        }
    }
}
