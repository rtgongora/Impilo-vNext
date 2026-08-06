package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.PolicyRuleRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Policy rules live on one plane; authorization is evaluated on another.
 *
 * <p>The estate runs two deliberate tenant planes. Rules are seeded only on the REGISTRY
 * plane — they are national reference data by the estate's own classification — while an
 * authenticated session presents the CARE plane. Every authenticated request therefore looked
 * up rules under a tenant holding none and denied {@code NO_MATCHING_RULES}. With ext_authz
 * enabled that denied the whole estate: a signed-in browser session was refused on 10 of 10
 * shell endpoints.
 *
 * <p>Merging is not widening. A merged rule is still evaluated in full, and the DENY-first
 * ordering PolicyEngine depends on must survive the union — which is why these tests assert
 * order, not just membership.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicyCacheGovernancePlaneTest {

    private static final UUID REGISTRY_PLANE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CARE_PLANE = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private PolicyRuleRepository ruleRepository;

    private PolicyCacheService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);   // always a cache miss: exercise the DB path
        service = new PolicyCacheService(redisTemplate, ruleRepository,
                new AuthzProperties(), new ObjectMapper());
    }

    private static PolicyRuleEntity rule(String name, String effect, int priority) {
        PolicyRuleEntity r = new PolicyRuleEntity();
        r.setName(name);
        r.setEffect(effect);
        r.setPriority(priority);
        return r;
    }

    @Test
    @DisplayName("a care-plane request sees the registry plane's governance rules")
    void carePlaneRequestSeesGovernanceRules() {
        when(ruleRepository.findByTenantIdAndActiveTrue(CARE_PLANE)).thenReturn(List.of());
        when(ruleRepository.findByTenantIdAndActiveTrue(REGISTRY_PLANE))
                .thenReturn(List.of(rule("shell-workspace-state", "ALLOW", 60)));

        List<PolicyRuleEntity> rules = service.getActiveRules(CARE_PLANE);

        assertThat(rules)
                .as("with none of these, every authenticated request denies NO_MATCHING_RULES")
                .extracting(PolicyRuleEntity::getName)
                .containsExactly("shell-workspace-state");
    }

    @Test
    @DisplayName("DENY still precedes ALLOW across the merged set, not just within each side")
    void denyOrderingSurvivesTheMerge() {
        // The repository returns each side already sorted. Concatenating them would leave this
        // governance DENY behind the tenant's ALLOW, silently inverting DENY-wins — a worse
        // outcome than the denial being fixed.
        when(ruleRepository.findByTenantIdAndActiveTrue(CARE_PLANE))
                .thenReturn(List.of(rule("tenant-allow", "ALLOW", 10)));
        when(ruleRepository.findByTenantIdAndActiveTrue(REGISTRY_PLANE))
                .thenReturn(List.of(rule("governance-deny", "DENY", 90)));

        assertThat(service.getActiveRules(CARE_PLANE))
                .extracting(PolicyRuleEntity::getName)
                .containsExactly("governance-deny", "tenant-allow");
    }

    @Test
    @DisplayName("within the same effect, lower priority still wins")
    void priorityOrderingIsPreserved() {
        when(ruleRepository.findByTenantIdAndActiveTrue(CARE_PLANE))
                .thenReturn(List.of(rule("tenant-allow-50", "ALLOW", 50)));
        when(ruleRepository.findByTenantIdAndActiveTrue(REGISTRY_PLANE))
                .thenReturn(List.of(rule("governance-allow-10", "ALLOW", 10)));

        assertThat(service.getActiveRules(CARE_PLANE))
                .extracting(PolicyRuleEntity::getName)
                .containsExactly("governance-allow-10", "tenant-allow-50");
    }

    @Test
    @DisplayName("the governance tenant itself is not merged with a copy of itself")
    void governanceTenantIsNotSelfMerged() {
        when(ruleRepository.findByTenantIdAndActiveTrue(REGISTRY_PLANE))
                .thenReturn(List.of(rule("only-once", "ALLOW", 10)));

        assertThat(service.getActiveRules(REGISTRY_PLANE))
                .extracting(PolicyRuleEntity::getName)
                .containsExactly("only-once");
    }

    @Test
    @DisplayName("a blank governance tenant disables the merge — strictly per-tenant rules")
    void blankGovernanceTenantDisablesTheMerge() {
        AuthzProperties props = new AuthzProperties();
        props.setGovernanceTenantId("");
        PolicyCacheService strict = new PolicyCacheService(
                redisTemplate, ruleRepository, props, new ObjectMapper());
        when(ruleRepository.findByTenantIdAndActiveTrue(CARE_PLANE)).thenReturn(List.of());

        assertThat(strict.getActiveRules(CARE_PLANE)).isEmpty();
    }

    @Test
    @DisplayName("a malformed governance tenant does not silently widen or crash")
    void malformedGovernanceTenantFallsBackToOwnRules() {
        // It must not throw on the request path, and it must not pretend to have merged.
        AuthzProperties props = new AuthzProperties();
        props.setGovernanceTenantId("not-a-uuid");
        PolicyCacheService broken = new PolicyCacheService(
                redisTemplate, ruleRepository, props, new ObjectMapper());
        when(ruleRepository.findByTenantIdAndActiveTrue(eq(CARE_PLANE)))
                .thenReturn(List.of(rule("tenant-own", "ALLOW", 10)));

        assertThat(broken.getActiveRules(CARE_PLANE))
                .extracting(PolicyRuleEntity::getName)
                .containsExactly("tenant-own");
    }
}
