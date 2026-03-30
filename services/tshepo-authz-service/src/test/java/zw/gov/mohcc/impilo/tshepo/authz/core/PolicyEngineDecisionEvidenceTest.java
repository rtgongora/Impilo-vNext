package zw.gov.mohcc.impilo.tshepo.authz.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DecisionEvidenceDto;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.PolicyDecisionLogRepository;
import zw.gov.mohcc.impilo.tshepo.authz.service.*;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Asserts that PolicyEngine populates and publishes a non-null DecisionEvidenceDto
 * with a policyVersion set on every authorization outcome (ALLOW, DENY, STEP_UP_REQUIRED).
 */
@ExtendWith(MockitoExtension.class)
class PolicyEngineDecisionEvidenceTest {

    @Mock private RiskScoring riskScoring;
    @Mock private PolicyCacheService policyCacheService;
    @Mock private ConsentClient consentClient;
    @Mock private StepUpService stepUpService;
    @Mock private BreakGlassService breakGlassService;
    @Mock private PolicyDecisionLogRepository decisionLogRepository;
    @Mock private AuditPublisher auditPublisher;
    @Mock private DecisionEvidencePublisher decisionEvidencePublisher;

    private PolicyEngine policyEngine;

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CORRELATION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID FACILITY_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final String ACTOR_ID = "actor-test";
    private static final String POLICY_VERSION = "2.1";

    @BeforeEach
    void setUp() {
        AuthzProperties props = new AuthzProperties();
        AuthzProperties.RiskThresholds thresholds = new AuthzProperties.RiskThresholds();
        thresholds.setDenyThreshold(81);
        thresholds.setStepUpTrigger(61);
        thresholds.setUnknownDeviceScore(70);
        thresholds.setNewDeviceScore(50);
        thresholds.setBlockedDeviceScore(100);
        props.setRiskThresholds(thresholds);
        props.setStepUpMethods(List.of("MFA", "BIOMETRIC"));
        props.setStepUpWindowSeconds(300);
        props.setBreakGlassTtlMinutes(60);
        props.setPolicyVersion(POLICY_VERSION);

        policyEngine = new PolicyEngine(
                riskScoring, policyCacheService, consentClient,
                stepUpService, breakGlassService, decisionLogRepository,
                auditPublisher, decisionEvidencePublisher, props, new ObjectMapper()
        );
    }

    // ── ALLOW path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("ALLOW decision: DecisionEvidenceDto is published with correct fields")
    void allowDecision_publishesDecisionEvidence() {
        when(riskScoring.score(eq(TENANT_ID), any(), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity allow = allowRule("facilities");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(allow));

        AuthzInternalRequest request = buildRequest("TREATMENT", "GET:/v1/facilities", "facilities", null);
        policyEngine.evaluate(request);

        ArgumentCaptor<DecisionEvidenceDto> captor = ArgumentCaptor.forClass(DecisionEvidenceDto.class);
        verify(decisionEvidencePublisher).publish(captor.capture());

        DecisionEvidenceDto evidence = captor.getValue();
        assertThat(evidence).isNotNull();
        assertThat(evidence.actorId()).isEqualTo(ACTOR_ID);
        assertThat(evidence.actorType()).isEqualTo("PROVIDER");
        assertThat(evidence.action()).isEqualTo("GET:/v1/facilities");
        assertThat(evidence.decision()).isEqualTo("ALLOW");
        assertThat(evidence.policyVersion()).isEqualTo(POLICY_VERSION);
        assertThat(evidence.consistencyClass()).isEqualTo("C");
        assertThat(evidence.projectionStalenessMs()).isNotNull().isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("ALLOW decision on POST: consistencyClass is B")
    void allowDecision_postAction_consistencyClassB() {
        when(riskScoring.score(eq(TENANT_ID), any(), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity allow = allowRule("facilities");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(allow));

        AuthzInternalRequest request = buildRequest("TREATMENT", "POST:/v1/facilities", "facilities", null);
        policyEngine.evaluate(request);

        ArgumentCaptor<DecisionEvidenceDto> captor = ArgumentCaptor.forClass(DecisionEvidenceDto.class);
        verify(decisionEvidencePublisher).publish(captor.capture());

        assertThat(captor.getValue().consistencyClass()).isEqualTo("B");
    }

    @Test
    @DisplayName("ALLOW decision on DELETE: consistencyClass is A")
    void allowDecision_deleteAction_consistencyClassA() {
        when(riskScoring.score(eq(TENANT_ID), any(), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity allow = allowRule("facilities");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(allow));

        AuthzInternalRequest request = buildRequest("TREATMENT", "DELETE:/v1/facilities/123", "facilities", null);
        policyEngine.evaluate(request);

        ArgumentCaptor<DecisionEvidenceDto> captor = ArgumentCaptor.forClass(DecisionEvidenceDto.class);
        verify(decisionEvidencePublisher).publish(captor.capture());

        assertThat(captor.getValue().consistencyClass()).isEqualTo("A");
    }

    // ── DENY paths ────────────────────────────────────────────────────────

    @Test
    @DisplayName("DENY on missing tenant: DecisionEvidenceDto is published with DENY decision")
    void denyDecision_missingTenant_publishesDecisionEvidence() {
        AuthzInternalRequest request = new AuthzInternalRequest(
                null, ACTOR_ID, "PROVIDER", List.of("DOCTOR"),
                "TREATMENT", "fp-abc", null, FACILITY_ID, null,
                null, "GET", "/v1/patients",
                "GET:/v1/patients", "patients", null,
                3, "session-x", null
        );

        policyEngine.evaluate(request);

        ArgumentCaptor<DecisionEvidenceDto> captor = ArgumentCaptor.forClass(DecisionEvidenceDto.class);
        verify(decisionEvidencePublisher).publish(captor.capture());

        DecisionEvidenceDto evidence = captor.getValue();
        assertThat(evidence.decision()).isEqualTo("DENY");
        assertThat(evidence.policyVersion()).isEqualTo(POLICY_VERSION);
        assertThat(evidence.reasonCodes()).contains("MISSING_TENANT");
    }

    @Test
    @DisplayName("DENY on device risk threshold: policyVersion is populated")
    void denyDecision_deviceBlocked_policyVersionPopulated() {
        when(riskScoring.score(eq(TENANT_ID), any(), eq(ACTOR_ID))).thenReturn(90);

        AuthzInternalRequest request = buildRequest("TREATMENT", "GET:/v1/patients", "patients", null);
        policyEngine.evaluate(request);

        ArgumentCaptor<DecisionEvidenceDto> captor = ArgumentCaptor.forClass(DecisionEvidenceDto.class);
        verify(decisionEvidencePublisher).publish(captor.capture());

        DecisionEvidenceDto evidence = captor.getValue();
        assertThat(evidence.decision()).isEqualTo("DENY");
        assertThat(evidence.policyVersion()).isNotBlank().isEqualTo(POLICY_VERSION);
    }

    // ── STEP_UP path ──────────────────────────────────────────────────────

    @Test
    @DisplayName("STEP_UP decision: DecisionEvidenceDto is published with STEP_UP_REQUIRED decision")
    void stepUpDecision_publishesDecisionEvidence() {
        when(riskScoring.score(eq(TENANT_ID), any(), eq(ACTOR_ID))).thenReturn(65);
        PolicyRuleEntity allow = allowRule("facilities");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(allow));
        when(stepUpService.hasRecentStepUp(eq(TENANT_ID), eq(ACTOR_ID))).thenReturn(false);

        AuthzInternalRequest request = buildRequest("TREATMENT", "DELETE:/v1/facilities/123", "facilities", null);
        policyEngine.evaluate(request);

        ArgumentCaptor<DecisionEvidenceDto> captor = ArgumentCaptor.forClass(DecisionEvidenceDto.class);
        verify(decisionEvidencePublisher).publish(captor.capture());

        DecisionEvidenceDto evidence = captor.getValue();
        assertThat(evidence.decision()).isEqualTo("STEP_UP_REQUIRED");
        assertThat(evidence.policyVersion()).isEqualTo(POLICY_VERSION);
        assertThat(evidence.reasonCodes()).contains("STEP_UP_REQUIRED");
    }

    // ── Clinical patientReference ──────────────────────────────────────────

    @Test
    @DisplayName("ALLOW on clinical resource: patientReference is set to resourceId")
    void allowDecision_clinicalResource_patientReferenceSet() {
        when(riskScoring.score(eq(TENANT_ID), any(), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity allow = allowRule("Patient");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(allow));
        when(consentClient.evaluateConsent(any(), any(), any(), any(), any()))
                .thenReturn(ConsentDecision.permit("consent-001", null));

        AuthzInternalRequest request = buildRequest("TREATMENT", "GET:/v1/Patient/patient-456", "Patient", "patient-456");
        policyEngine.evaluate(request);

        ArgumentCaptor<DecisionEvidenceDto> captor = ArgumentCaptor.forClass(DecisionEvidenceDto.class);
        verify(decisionEvidencePublisher).publish(captor.capture());

        assertThat(captor.getValue().patientReference()).isEqualTo("patient-456");
    }

    // ── AuditPublisher policyVersion forwarding ───────────────────────────

    @Test
    @DisplayName("AuditPublisher receives policyVersion on every ALLOW decision")
    void allowDecision_auditPublisherReceivesPolicyVersion() {
        when(riskScoring.score(eq(TENANT_ID), any(), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity allow = allowRule("facilities");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(allow));

        AuthzInternalRequest request = buildRequest("TREATMENT", "GET:/v1/facilities", "facilities", null);
        policyEngine.evaluate(request);

        verify(auditPublisher).queueAuditEvent(eq(request), eq("ALLOW"), anyInt(), isNull(), eq(POLICY_VERSION));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private AuthzInternalRequest buildRequest(String purpose, String action,
                                               String resourceType, String resourceId) {
        return new AuthzInternalRequest(
                TENANT_ID, ACTOR_ID, "PROVIDER", List.of("DOCTOR"),
                purpose, "fp-abc", CORRELATION_ID, FACILITY_ID, null,
                null, "GET", "/v1/patients",
                action, resourceType, resourceId,
                3, "session-x", null
        );
    }

    private PolicyRuleEntity allowRule(String resourceType) {
        PolicyRuleEntity rule = new PolicyRuleEntity();
        rule.setTenantId(TENANT_ID);
        rule.setName("allow-rule");
        rule.setResourceType(resourceType);
        rule.setEffect("ALLOW");
        rule.setFacilityScope(false);
        rule.setWorkspaceScope(false);
        rule.setActive(true);
        rule.setPriority(100);
        return rule;
    }
}
