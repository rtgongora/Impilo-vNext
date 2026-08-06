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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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
        List<PolicyRuleEntity> rules = mergeGovernanceRules(
                tenantId, ruleRepository.findByTenantIdAndActiveTrue(tenantId));

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
     * Merge the governance tenant's rules into a tenant's own set.
     *
     * <p>Policy rules live on the REGISTRY plane, because rules are national reference data by
     * the estate's own classification. Authorization is evaluated under whatever plane the
     * request presents, and an authenticated session presents the CARE plane — so every
     * authenticated request looked up rules under a tenant holding none and denied with
     * {@code NO_MATCHING_RULES}. Measured with ext_authz enabled: a signed-in browser session
     * was refused on 10 of 10 shell endpoints.
     *
     * <p><strong>Ordering is re-established across the union, never concatenated.</strong> The
     * repository returns {@code ORDER BY effect DESC, priority ASC} — DENY before ALLOW, then
     * by priority — and PolicyEngine relies on it: DENY wins, first matching ALLOW is taken.
     * Appending one sorted list to another would leave a governance DENY sitting behind a
     * tenant ALLOW and silently invert precedence, which is worse than the denial this fixes.
     *
     * <p>This widens which rules are <em>found</em>, not what they permit. Every merged rule is
     * still evaluated in full against actor, role, purpose, scope and conditions — and a
     * governance DENY now reaches requests it previously never saw.
     */
    private List<PolicyRuleEntity> mergeGovernanceRules(UUID tenantId, List<PolicyRuleEntity> own) {
        String configured = properties.getGovernanceTenantId();
        if (configured == null || configured.isBlank()) {
            return own;   // merge deliberately disabled: strictly per-tenant rules
        }
        UUID governanceTenant;
        try {
            governanceTenant = UUID.fromString(configured.trim());
        } catch (IllegalArgumentException e) {
            // A malformed value must not quietly disable the merge: that restores the
            // deny-everything behaviour while the configuration claims to prevent it.
            log.error("tshepo.authz.governance-tenant-id is not a UUID ('{}'); rules will NOT be "
                    + "merged and non-governance tenants may deny NO_MATCHING_RULES", configured);
            return own;
        }
        if (governanceTenant.equals(tenantId)) {
            return own;   // already the governance tenant
        }

        List<PolicyRuleEntity> governance = ruleRepository.findByTenantIdAndActiveTrue(governanceTenant);
        if (governance.isEmpty()) {
            return own;
        }
        log.debug("Merged {} governance rules (tenant {}) into {} own rules for tenant {}",
                governance.size(), governanceTenant, own.size(), tenantId);

        return Stream.concat(own.stream(), governance.stream())
                .sorted(Comparator.comparingInt(PolicyCacheService::effectRank)
                        .thenComparingInt(PolicyCacheService::priorityOf))
                .toList();
    }

    /** DENY sorts before ALLOW, mirroring the repository's {@code ORDER BY effect DESC}. */
    private static int effectRank(PolicyRuleEntity rule) {
        return "DENY".equalsIgnoreCase(rule.getEffect()) ? 0 : 1;
    }

    /** Priority is a primitive on the entity, so there is no absent case to defend against. */
    private static int priorityOf(PolicyRuleEntity rule) {
        return rule.getPriority();
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
