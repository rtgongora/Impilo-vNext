package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the OPA {@code data} bundle from the {@code policy_rule} rows (Phase 7 / A3).
 *
 * <p>Snapshots active rules into the shape the {@code impilo.policy.decision} rego (A4) consumes —
 * {@code data.policy.rules[]} — so OPA can reproduce the Java PolicyEngine verdict. The JSONB
 * {@code conditions} string is parsed into a real object (the rego reads {@code c.min_loa} etc.);
 * {@code tenant_id} is stringified to match the canonical OPA input. Rules are emitted in the same
 * order the Java cache evaluates them: {@code effect DESC, priority ASC} (all DENY before all ALLOW).
 *
 * <p>This is the snapshot/serialisation half of the bundle pipeline; versioned delivery + push/reload
 * to a running OPA is the live-gated runtime (Stage B).
 */
@Component
public class PolicyRuleBundleBuilder {

    private static final Logger log = LoggerFactory.getLogger(PolicyRuleBundleBuilder.class);

    private final ObjectMapper objectMapper;

    public PolicyRuleBundleBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Build the OPA data document: {@code { "policy": { "rules": [ ... ] } }}.
     * The rules are ordered effect DESC, priority ASC to mirror the cache evaluation order.
     */
    public Map<String, Object> buildBundle(List<PolicyRuleEntity> rules) {
        List<Map<String, Object>> ruleObjects = new ArrayList<>();
        rules.stream()
                .sorted((a, b) -> {
                    int byEffect = String.valueOf(b.getEffect()).compareTo(String.valueOf(a.getEffect())); // DESC
                    if (byEffect != 0) {
                        return byEffect;
                    }
                    return Integer.compare(a.getPriority(), b.getPriority()); // priority ASC
                })
                .forEach(r -> ruleObjects.add(toRuleObject(r)));

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("rules", ruleObjects);
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("policy", policy);
        return bundle;
    }

    /** Serialise the bundle to JSON (the artifact pushed to OPA / written to the bundle volume). */
    public String buildBundleJson(List<PolicyRuleEntity> rules) {
        try {
            return objectMapper.writeValueAsString(buildBundle(rules));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise OPA policy bundle", e);
        }
    }

    private Map<String, Object> toRuleObject(PolicyRuleEntity r) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("name", r.getName());
        o.put("tenant_id", r.getTenantId() == null ? null : r.getTenantId().toString());
        o.put("priority", r.getPriority());
        o.put("effect", r.getEffect());
        o.put("actor_type", r.getActorType());
        o.put("role", r.getRole());
        o.put("action", r.getAction());
        o.put("purpose", r.getPurpose());
        o.put("resource_type", r.getResourceType());
        o.put("facility_scope", r.isFacilityScope());
        o.put("workspace_scope", r.isWorkspaceScope());
        o.put("conditions", parseConditions(r.getConditions(), r.getName()));
        return o;
    }

    /** Parse the JSONB conditions string into an object; null/blank/invalid -> empty (unconditional). */
    private Map<String, Object> parseConditions(String conditionsJson, String ruleName) {
        if (conditionsJson == null || conditionsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(conditionsJson, new TypeReference<>() {});
        } catch (Exception e) {
            // Mirror PolicyEngine's fail-closed stance: an unparseable condition must not silently
            // become unconditional. Emit a sentinel that the rego treats as never-satisfied.
            log.warn("Rule '{}' has unparseable conditions; emitting fail-closed sentinel: {}",
                    ruleName, e.getMessage());
            return Map.of("__unparseable__", true);
        }
    }
}
