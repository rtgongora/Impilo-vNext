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
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyDecisionLogEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.PolicyDecisionLogRepository;
import zw.gov.mohcc.impilo.tshepo.authz.service.*;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Constrained recovery gate (Step 0): a session authenticated with recovery codes never
 * carries ordinary workforce AAL2 authority. Everything outside the recovery allowlist
 * returns the canonical RECOVERY_REQUIRED decision, expressed fail-closed on the legacy
 * wire as DENY UNREPRESENTABLE_RECOVERY_REQUIRED.
 */
@ExtendWith(MockitoExtension.class)
class ConstrainedRecoveryPolicyTest {

    @Mock private DeviceRiskScoreEvaluator riskScoring;
    @Mock private PolicyCacheService policyCacheService;
    @Mock private ProviderPrivilegeRevocationStore privilegeRevocationStore;
    @Mock private ConsentClient consentClient;
    @Mock private StepUpService stepUpService;
    @Mock private BreakGlassService breakGlassService;
    @Mock private PolicyDecisionLogRepository decisionLogRepository;
    @Mock private AuditPublisher auditPublisher;
    @Mock private VisibilityEscalationService visibilityEscalationService;
    @Mock private DelegationClient delegationClient;
    @Mock private OpaDecisionClient opaDecisionClient;
    @Mock private RoleTemplateCatalog roleTemplateCatalog;
    @Mock private ConfidentialityPolicyPack confidentialityPack;
    @Mock private DecisionEnvelopeSigner decisionEnvelopeSigner;

    private PolicyEngine policyEngine;

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CORRELATION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private static final AuthenticationAssurance RECOVERY_ASSURANCE = new AuthenticationAssurance(
            2, List.of("pwd", "auth-recovery-authn-code-form"), Instant.now(), null,
            false, "recovery-session", null);

    private static final AuthenticationAssurance ORDINARY_AAL2 = new AuthenticationAssurance(
            2, List.of("pwd", "otp"), Instant.now(), null,
            false, "ordinary-session", null);

    @BeforeEach
    void setUp() {
        lenient().when(privilegeRevocationStore.isRevoked(anyString())).thenReturn(false);
        lenient().when(visibilityEscalationService.resolveActiveGrant(any())).thenReturn(Optional.empty());
        lenient().when(policyCacheService.getActiveRulesForResource(any(), any())).thenReturn(List.of());
        policyEngine = new PolicyEngine(
                riskScoring, policyCacheService, privilegeRevocationStore, consentClient,
                stepUpService, breakGlassService, decisionLogRepository,
                auditPublisher, new AuthzProperties(), new ObjectMapper(), visibilityEscalationService,
                delegationClient, opaDecisionClient, roleTemplateCatalog, confidentialityPack,
                decisionEnvelopeSigner
        );
    }

    private static AuthzInternalRequest request(String action, String resourceType,
                                                AuthenticationAssurance assurance,
                                                String escalationGrantId) {
        return new AuthzInternalRequest(
                TENANT_ID, "actor-recovery", "PROVIDER", List.of("DOCTOR"), "TREATMENT",
                "fp-device", CORRELATION_ID, null, null,
                null, "GET", "/v1/patients", action, resourceType, null,
                3, "session-abc", null, assurance,
                null, null, null, null, null, null,
                escalationGrantId, null, DutyContext.absent()
        );
    }

    @Test
    @DisplayName("Recovery session attempting a clinical read is refused with RECOVERY_REQUIRED")
    void recoverySessionDeniedForClinicalRead() {
        AuthzResponse response = policyEngine.evaluate(
                request("GET:/v1/patients", "patients", RECOVERY_ASSURANCE, null));

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("UNREPRESENTABLE_RECOVERY_REQUIRED", response.errorCode());

        ArgumentCaptor<PolicyDecisionLogEntity> captor =
                ArgumentCaptor.forClass(PolicyDecisionLogEntity.class);
        verify(decisionLogRepository).save(captor.capture());
        assertEquals("RECOVERY_REQUIRED", captor.getValue().getVerdict());
        assertEquals("CONSTRAINED_RECOVERY_SESSION", captor.getValue().getDenyReason());
    }

    @Test
    @DisplayName("Recovery session is refused for sensitive/administrative actions, not stepped up")
    void recoverySessionNeverStepsUpIntoAuthority() {
        for (String action : List.of("DELETE:/v1/patients/1", "EXPORT:/v1/reports",
                "POST:/v1/claims", "PUT:/v1/admin/users")) {
            AuthzResponse response = policyEngine.evaluate(
                    request(action, "patients", RECOVERY_ASSURANCE, null));
            assertEquals(Verdict.DENY, response.verdict(), action);
            assertEquals("UNREPRESENTABLE_RECOVERY_REQUIRED", response.errorCode(), action);
        }
    }

    @Test
    @DisplayName("Recovery allowlist actions pass the gate and continue through ordinary policy")
    void recoveryAllowlistActionsPassGate() {
        AuthzResponse factorRead = policyEngine.evaluate(
                request("READ", "ACCOUNT_SECURITY", RECOVERY_ASSURANCE, null));
        // Continues into RBAC (no rules seeded -> NO_MATCHING_RULES), proving the recovery
        // gate exempted it rather than blanket-denying with RECOVERY_REQUIRED.
        assertEquals("NO_MATCHING_RULES", factorRead.errorCode());

        AuthzResponse logout = policyEngine.evaluate(
                request("LOGOUT", "anything", RECOVERY_ASSURANCE, null));
        assertEquals("NO_MATCHING_RULES", logout.errorCode());

        AuthzResponse sessionTermination = policyEngine.evaluate(
                request("DELETE", "AUTH_SESSION", RECOVERY_ASSURANCE, null));
        assertEquals("NO_MATCHING_RULES", sessionTermination.errorCode());
    }

    @Test
    @DisplayName("Recovery session cannot activate a visibility-escalation grant")
    void recoverySessionCannotActivateEscalation() {
        AuthzResponse response = policyEngine.evaluate(
                request("GET:/v1/patients", "patients", RECOVERY_ASSURANCE, "grant-123"));

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("UNREPRESENTABLE_RECOVERY_REQUIRED", response.errorCode());
        verify(visibilityEscalationService, never()).resolveActiveGrant(any());
    }

    @Test
    @DisplayName("Ordinary AAL2 session is unaffected by the recovery gate")
    void ordinaryAal2SessionUnaffected() {
        AuthzResponse response = policyEngine.evaluate(
                request("GET:/v1/patients", "patients", ORDINARY_AAL2, null));
        assertNotEquals("UNREPRESENTABLE_RECOVERY_REQUIRED", response.errorCode());
    }

    @Test
    @DisplayName("Absent assurance is unaffected by the recovery gate")
    void absentAssuranceUnaffected() {
        AuthzResponse response = policyEngine.evaluate(
                request("GET:/v1/patients", "patients", null, null));
        assertNotEquals("UNREPRESENTABLE_RECOVERY_REQUIRED", response.errorCode());
    }

    @Test
    @DisplayName("Recovery codes are no longer an advertised step-up method")
    void recoveryNotAStepUpMethod() {
        assertFalse(new AuthzProperties().getStepUpMethods().contains("recovery"),
                "recovery must not satisfy step-up: it yields a constrained session only");
    }
}
