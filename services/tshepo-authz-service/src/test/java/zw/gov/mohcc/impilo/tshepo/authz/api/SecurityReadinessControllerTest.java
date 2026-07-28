package zw.gov.mohcc.impilo.tshepo.authz.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.PolicyRuleRepository;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityReadinessControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private PolicyRuleRepository policyRuleRepository;

    @Test
    void selfReportedEnforcing_falseWhileWorkContextModeIsShadow() {
        AuthzProperties props = new AuthzProperties();
        props.setWorkContextMode("SHADOW");
        when(policyRuleRepository.countByTenantId(TENANT_ID)).thenReturn(50L);
        when(policyRuleRepository.countByTenantIdAndActiveTrue(TENANT_ID)).thenReturn(45L);
        when(policyRuleRepository.countByTenantIdAndNameStartingWith(TENANT_ID, "work-mode-")).thenReturn(8L);
        when(policyRuleRepository.countByTenantIdAndNameStartingWithAndActiveTrue(TENANT_ID, "work-mode-")).thenReturn(0L);
        when(policyRuleRepository.countByTenantIdAndNameStartingWith(TENANT_ID, "rom-")).thenReturn(3L);
        when(policyRuleRepository.countByTenantIdAndNameStartingWithAndActiveTrue(TENANT_ID, "rom-")).thenReturn(0L);

        SecurityReadinessController controller = new SecurityReadinessController(props, policyRuleRepository);
        ResponseEntity<Map<String, Object>> response = controller.readiness(TENANT_ID);

        assertThat(response.getBody().get("workContextEnforcing")).isEqualTo(false);
        assertThat(response.getBody().get("workModeBoundaryRulesEnforcing")).isEqualTo(false);
        assertThat(response.getBody().get("selfReportedEnforcing")).isEqualTo(false);
        // Never leaks rule content — only counts.
        assertThat(response.getBody()).doesNotContainKey("rules");
    }

    @Test
    void selfReportedEnforcing_trueOnlyWhenEveryDimensionIsFlipped() {
        AuthzProperties props = new AuthzProperties();
        props.setWorkContextMode("ENFORCE");
        when(policyRuleRepository.countByTenantId(TENANT_ID)).thenReturn(50L);
        when(policyRuleRepository.countByTenantIdAndActiveTrue(TENANT_ID)).thenReturn(50L);
        when(policyRuleRepository.countByTenantIdAndNameStartingWith(TENANT_ID, "work-mode-")).thenReturn(8L);
        when(policyRuleRepository.countByTenantIdAndNameStartingWithAndActiveTrue(TENANT_ID, "work-mode-")).thenReturn(8L);
        when(policyRuleRepository.countByTenantIdAndNameStartingWith(TENANT_ID, "rom-")).thenReturn(3L);
        when(policyRuleRepository.countByTenantIdAndNameStartingWithAndActiveTrue(TENANT_ID, "rom-")).thenReturn(3L);

        SecurityReadinessController controller = new SecurityReadinessController(props, policyRuleRepository);
        ResponseEntity<Map<String, Object>> response = controller.readiness(TENANT_ID);

        assertThat(response.getBody().get("selfReportedEnforcing")).isEqualTo(true);
    }
}
