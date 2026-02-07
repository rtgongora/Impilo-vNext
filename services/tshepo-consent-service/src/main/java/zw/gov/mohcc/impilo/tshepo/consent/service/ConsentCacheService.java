package zw.gov.mohcc.impilo.tshepo.consent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.tshepo.consent.config.ConsentProperties;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis cache service for consent evaluation decisions.
 *
 * <p>Cache key format: {@code consent:eval:{tenantId}:{patientRef}:{granteeRef}:{purpose}:{scope}}</p>
 *
 * <p>Cache is invalidated on any consent mutation for the affected patient to ensure
 * consistency between stored consents and cached evaluation results.</p>
 */
@Service
public class ConsentCacheService {

    private static final Logger log = LoggerFactory.getLogger(ConsentCacheService.class);
    private static final String CACHE_KEY_PREFIX = "consent:eval:";
    private static final String PATIENT_KEY_PREFIX = "consent:patient:";

    private final StringRedisTemplate redisTemplate;
    private final ConsentProperties properties;
    private final ObjectMapper objectMapper;

    public ConsentCacheService(StringRedisTemplate redisTemplate,
                                ConsentProperties properties,
                                ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Builds the Redis cache key for a consent evaluation.
     */
    public String buildCacheKey(UUID tenantId, String patientRef, String granteeRef,
                                 String purpose, String scope) {
        return CACHE_KEY_PREFIX + tenantId + ":" + patientRef + ":" + granteeRef
                + ":" + purpose + ":" + scope;
    }

    /**
     * Builds the Redis set key that tracks all evaluation cache keys for a patient.
     */
    private String buildPatientSetKey(UUID tenantId, String patientRef) {
        return PATIENT_KEY_PREFIX + tenantId + ":" + patientRef;
    }

    /**
     * Retrieves a cached consent decision, if available.
     */
    public Optional<ConsentDecision> getCachedDecision(UUID tenantId, String patientRef,
                                                        String granteeRef, String purpose,
                                                        String scope) {
        String key = buildCacheKey(tenantId, patientRef, granteeRef, purpose, scope);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                ConsentDecision decision = objectMapper.readValue(json, ConsentDecision.class);
                log.debug("Cache hit for key={}", key);
                return Optional.of(decision);
            }
        } catch (Exception e) {
            log.warn("Failed to read consent decision from cache key={}: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Caches a consent evaluation decision with the configured TTL.
     * Also tracks the cache key in a patient-level set for bulk invalidation.
     */
    public void cacheDecision(UUID tenantId, String patientRef, String granteeRef,
                               String purpose, String scope, ConsentDecision decision) {
        String key = buildCacheKey(tenantId, patientRef, granteeRef, purpose, scope);
        Duration ttl = Duration.ofSeconds(properties.getCacheTtlSeconds());
        try {
            String json = objectMapper.writeValueAsString(decision);
            redisTemplate.opsForValue().set(key, json, ttl);

            // Track this key in the patient's key set for invalidation
            String patientSetKey = buildPatientSetKey(tenantId, patientRef);
            redisTemplate.opsForSet().add(patientSetKey, key);
            redisTemplate.expire(patientSetKey, ttl.plusMinutes(5));

            log.debug("Cached consent decision key={} ttl={}s", key, properties.getCacheTtlSeconds());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize consent decision for caching key={}: {}", key, e.getMessage());
        }
    }

    /**
     * Invalidates all cached consent decisions for a specific patient.
     * Called after any consent mutation (create, update, revoke) for the patient.
     */
    public void invalidateForPatient(UUID tenantId, String patientRef) {
        String patientSetKey = buildPatientSetKey(tenantId, patientRef);
        try {
            Set<String> keys = redisTemplate.opsForSet().members(patientSetKey);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Invalidated {} cached consent decisions for patient={}", keys.size(), patientRef);
            }
            redisTemplate.delete(patientSetKey);
        } catch (Exception e) {
            log.warn("Failed to invalidate consent cache for patient={}: {}", patientRef, e.getMessage());
        }
    }
}
