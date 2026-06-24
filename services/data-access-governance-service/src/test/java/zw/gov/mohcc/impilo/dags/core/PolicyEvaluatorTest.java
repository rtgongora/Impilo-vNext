package zw.gov.mohcc.impilo.dags.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.dags.persistence.entity.PolicyEntity;
import zw.gov.mohcc.impilo.dags.persistence.repository.PolicyRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyEvaluatorTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-0000000000d1");

    @Mock PolicyRepository policyRepository;

    private PolicyEntity policy(String conditions, PolicyEffect effect, String name) {
        PolicyEntity p = new PolicyEntity();
        p.setResourceType("PATIENT_RECORD");
        p.setConditions(conditions);
        p.setEffect(effect);
        p.setName(name);
        return p;
    }

    @Test
    void allowsWhenConditionMatches() {
        PolicyEvaluator svc = new PolicyEvaluator(policyRepository);
        when(policyRepository.findByTenantIdAndResourceType(TENANT, "PATIENT_RECORD"))
                .thenReturn(List.of(policy("{\"purpose\":\"TREATMENT\"}", PolicyEffect.ALLOW, "treat-allow")));

        var d = svc.evaluate(TENANT, "PATIENT_RECORD", Map.of("purpose", "TREATMENT"));

        assertThat(d.effect()).isEqualTo(PolicyEffect.ALLOW);
        assertThat(d.reason()).contains("treat-allow");
    }

    @Test
    void denyOverridesAllow() {
        PolicyEvaluator svc = new PolicyEvaluator(policyRepository);
        when(policyRepository.findByTenantIdAndResourceType(TENANT, "PATIENT_RECORD"))
                .thenReturn(List.of(
                        policy("{\"purpose\":\"TREATMENT\"}", PolicyEffect.ALLOW, "allow-1"),
                        policy("{\"purpose\":\"TREATMENT\"}", PolicyEffect.DENY, "deny-1")));

        var d = svc.evaluate(TENANT, "PATIENT_RECORD", Map.of("purpose", "TREATMENT"));

        assertThat(d.effect()).isEqualTo(PolicyEffect.DENY);
        assertThat(d.matchedPolicyId()).isNull(); // entity has no id set in unit test; reason names the policy
        assertThat(d.reason()).contains("deny-1");
    }

    @Test
    void defaultDenyWhenNoConditionMatches() {
        PolicyEvaluator svc = new PolicyEvaluator(policyRepository);
        when(policyRepository.findByTenantIdAndResourceType(TENANT, "PATIENT_RECORD"))
                .thenReturn(List.of(policy("{\"purpose\":\"RESEARCH\"}", PolicyEffect.ALLOW, "research-allow")));

        var d = svc.evaluate(TENANT, "PATIENT_RECORD", Map.of("purpose", "TREATMENT"));

        assertThat(d.effect()).isEqualTo(PolicyEffect.DENY);
        assertThat(d.reason()).contains("default deny");
    }

    @Test
    void unconditionalPolicyMatchesEverything() {
        PolicyEvaluator svc = new PolicyEvaluator(policyRepository);
        when(policyRepository.findByTenantIdAndResourceType(TENANT, "PATIENT_RECORD"))
                .thenReturn(List.of(policy("{}", PolicyEffect.ALLOW, "open")));

        assertThat(svc.evaluate(TENANT, "PATIENT_RECORD", Map.of()).effect()).isEqualTo(PolicyEffect.ALLOW);
    }
}
