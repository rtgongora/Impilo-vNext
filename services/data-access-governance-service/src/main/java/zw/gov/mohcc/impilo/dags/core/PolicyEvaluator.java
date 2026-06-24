package zw.gov.mohcc.impilo.dags.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.dags.persistence.entity.PolicyEntity;
import zw.gov.mohcc.impilo.dags.persistence.repository.PolicyRepository;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evaluates a request against the persisted policy {@code conditions} (G040 — previously the
 * conditions column was stored but never read by any engine). A policy matches when every
 * condition key=value is satisfied by the request context (an empty {@code {}} matches
 * unconditionally). Decision rule: <b>deny-overrides</b>, then allow, else <b>default deny</b>.
 * Unparseable conditions never match (fail-closed).
 */
@Service
public class PolicyEvaluator {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PolicyRepository policyRepository;

    public PolicyEvaluator(PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    public record Decision(PolicyEffect effect, String matchedPolicyId, String reason) {}

    @Transactional(readOnly = true)
    public Decision evaluate(UUID tenantId, String resourceType, Map<String, String> context) {
        Map<String, String> ctx = context != null ? context : Map.of();
        List<PolicyEntity> policies = policyRepository.findByTenantIdAndResourceType(tenantId, resourceType);

        PolicyEntity matchedAllow = null;
        for (PolicyEntity p : policies) {
            if (!conditionsSatisfied(p.getConditions(), ctx)) {
                continue;
            }
            if (p.getEffect() == PolicyEffect.DENY) {
                // deny-overrides: a single matching DENY is decisive
                return new Decision(PolicyEffect.DENY, idOf(p), "Matched DENY policy: " + p.getName());
            }
            if (matchedAllow == null) {
                matchedAllow = p;
            }
        }
        if (matchedAllow != null) {
            return new Decision(PolicyEffect.ALLOW, idOf(matchedAllow),
                    "Matched ALLOW policy: " + matchedAllow.getName());
        }
        return new Decision(PolicyEffect.DENY, null, "No matching policy — default deny");
    }

    private static String idOf(PolicyEntity p) {
        return p.getId() != null ? p.getId().toString() : null;
    }

    private boolean conditionsSatisfied(String conditionsJson, Map<String, String> context) {
        if (conditionsJson == null || conditionsJson.isBlank() || "{}".equals(conditionsJson.trim())) {
            return true; // unconditional policy matches everything
        }
        try {
            JsonNode node = JSON.readTree(conditionsJson);
            if (!node.isObject()) {
                return false;
            }
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                String expected = e.getValue().asText();
                String actual = context.get(e.getKey());
                if (actual == null || !actual.equalsIgnoreCase(expected)) {
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            return false; // unparseable conditions never match (fail-closed)
        }
    }
}
