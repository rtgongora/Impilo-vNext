package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.PolicyRuleRepository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis-backed cache for policy rules.
 *
 * <p>The ext_authz endpoint is called on every request, so policy rules
 * must be loaded as fast as possible. This service caches active rules
 * per tenant in Redis with a configurable TTL (default 300s).</p>
 *
 * <p>Cache is invalidated when rules are created, updated, or deleted
 * via the PolicyManagementService.</p>
 */
@Service
public class PolicyCacheService {

    private static final Logger log = LoggerFactory.getLogger(PolicyCacheService.class);
    private static final String CACHE_KEY_PREFIX = "tshepo:authz:rules:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final PolicyRuleRepository ruleRepository;
    private final AuthzProperties properties;
    private final ObjectMapper objectMapper;

    public PolicyCacheService(RedisTemplate<String, Object> redisTemplate,
                              PolicyRuleRepository ruleRepository,
                              AuthzProperties properties,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.ruleRepository = ruleRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Get active policy rules for a tenant. Tries Redis first, falls back to DB.
     */
    public List<PolicyRuleEntity> getActiveRules(UUID tenantId) {
        String cacheKey = CACHE_KEY_PREFIX + tenantId;

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Policy rules cache HIT for tenant {}", tenantId);
                return objectMapper.convertValue(cached,
                        new TypeReference<List<PolicyRuleEntity>>() {});
            }
        } catch (Exception e) {
            log.warn("Redis read failed for policy rules (tenant {}), falling back to DB: {}",
                    tenantId, e.getMessage());
        }

        log.debug("Policy rules cache MISS for tenant {}, loading from DB", tenantId);
        List<PolicyRuleEntity> rules = ruleRepository.findByTenantIdAndActiveTrue(tenantId);

        try {
            redisTemplate.opsForValue().set(cacheKey, rules,
                    Duration.ofSeconds(properties.getCache().getPolicyRulesTtlSeconds()));
        } catch (Exception e) {
            log.warn("Redis write failed for policy rules (tenant {}): {}",
                    tenantId, e.getMessage());
        }

        return rules;
    }

    /**
     * Get active policy rules for a tenant and specific resource type.
     */
    public List<PolicyRuleEntity> getActiveRulesForResource(UUID tenantId, String resourceType) {
        List<PolicyRuleEntity> allRules = getActiveRules(tenantId);

        return allRules.stream()
                .filter(r -> r.getResourceType() == null ||
                             r.getResourceType().equals(resourceType) ||
                             resourceType.startsWith(r.getResourceType()))
                .toList();
    }

    /**
     * Invalidate the policy rules cache for a tenant.
     * Called after any CRUD operation on policy rules.
     */
    public void invalidate(UUID tenantId) {
        String cacheKey = CACHE_KEY_PREFIX + tenantId;
        try {
            redisTemplate.delete(cacheKey);
            log.debug("Policy rules cache invalidated for tenant {}", tenantId);
        } catch (Exception e) {
            log.warn("Redis delete failed for policy rules cache (tenant {}): {}",
                    tenantId, e.getMessage());
        }
    }

    /**
     * Evict cached policy rules for a tenant after a provider privilege change.
     *
     * <p>Rules are cached per tenant only; provider-specific invalidation is
     * expressed as a full tenant eviction (rare VARAPI revocation events).</p>
     *
     * @param tenantId string tenant UUID from the event envelope or payload; ignored if blank
     * @param providerId VARAPI provider public id (for logging only)
     */
    public void evictForProvider(String tenantId, String providerId) {
        if (tenantId == null || tenantId.isBlank()) {
            log.debug("Skipping policy rules eviction — no tenant id (provider_id={})", providerId);
            return;
        }
        try {
            invalidate(UUID.fromString(tenantId.trim()));
            log.debug("Policy rules cache evicted for tenant {} after provider {} change",
                    tenantId, providerId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant_id '{}' on provider privilege event (provider_id={})",
                    tenantId, providerId);
        }
    }
}
