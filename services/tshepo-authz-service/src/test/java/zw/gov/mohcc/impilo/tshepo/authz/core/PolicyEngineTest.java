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
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyDecisionLogEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.PolicyDecisionLogRepository;
import zw.gov.mohcc.impilo.tshepo.authz.service.*;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the PolicyEngine — the core policy decision point (PDP)
 * of the TSHEPO trust layer.
 *
 * <p>Tests the 7-step policy evaluation algorithm:
 * risk scoring, purpose validation, break-glass, RBAC/ABAC,
 * consent, risk-based step-up, and obligation computation.</p>
 */
@ExtendWith(MockitoExtension.class)
class PolicyEngineTest {

    @Mock private DeviceRiskScoreEvaluator riskScoring;
    @Mock private PolicyCacheService policyCacheService;
    @Mock private ProviderPrivilegeRevocationStore privilegeRevocationStore;
    @Mock private ConsentClient consentClient;
    @Mock private StepUpService stepUpService;
    @Mock private BreakGlassService breakGlassService;
    @Mock private PolicyDecisionLogRepository decisionLogRepository;
    @Mock private AuditPublisher auditPublisher;
    @Mock private VisibilityEscalationService visibilityEscalationService;

    private AuthzProperties properties;
    private ObjectMapper objectMapper;
    private PolicyEngine policyEngine;

    // Shared test fixtures
    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CORRELATION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID FACILITY_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final UUID WORKSPACE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String ACTOR_ID = "actor-123";
    private static final String DEVICE_FP = "fp-sha256-abcdef";

    @BeforeEach
    void setUp() {
        properties = buildDefaultProperties();
        objectMapper = new ObjectMapper();
        lenient().when(privilegeRevocationStore.isRevoked(anyString())).thenReturn(false);
        lenient().when(visibilityEscalationService.resolveActiveGrant(any())).thenReturn(Optional.empty());
        policyEngine = new PolicyEngine(
                riskScoring, policyCacheService, privilegeRevocationStore, consentClient,
                stepUpService, breakGlassService, decisionLogRepository,
                auditPublisher, properties, objectMapper, visibilityEscalationService
        );
    }

    // ════════════════════════════════════════════════════════════════════
    // Helper: Build AuthzInternalRequest with sensible defaults
    // ════════════════════════════════════════════════════════════════════

    private static AuthzInternalRequest buildRequest(
            UUID tenantId, String purposeOfUse, String action, String resourceType,
            String resourceId, String deviceFingerprint, UUID facilityId, UUID workspaceId,
            List<String> roles, String actorType, int loaLevel) {
        return new AuthzInternalRequest(
                tenantId, ACTOR_ID, actorType, roles, purposeOfUse,
                deviceFingerprint, CORRELATION_ID, facilityId, workspaceId,
                null, "GET", "/v1/patients", action, resourceType, resourceId,
                loaLevel, "session-abc", null,
                null, null, null, null, null, null,
                null, null
        );
    }

    private static AuthzInternalRequest defaultRequest() {
        return buildRequest(
                TENANT_ID, "TREATMENT", "GET:/v1/patients",
                "patients", null, DEVICE_FP, FACILITY_ID, WORKSPACE_ID,
                List.of("DOCTOR"), "PROVIDER", 3
        );
    }

    private static AuthzInternalRequest requestWithPurpose(String purpose) {
        return buildRequest(
                TENANT_ID, purpose, "GET:/v1/patients",
                "patients", null, DEVICE_FP, FACILITY_ID, WORKSPACE_ID,
                List.of("DOCTOR"), "PROVIDER", 3
        );
    }

    private static AuthzInternalRequest requestWithAction(String action, String resourceType) {
        return buildRequest(
                TENANT_ID, "TREATMENT", action,
                resourceType, null, DEVICE_FP, FACILITY_ID, WORKSPACE_ID,
                List.of("DOCTOR"), "PROVIDER", 3
        );
    }

    private static AuthzInternalRequest requestWithResourceId(String resourceType, String resourceId) {
        return buildRequest(
                TENANT_ID, "TREATMENT", "GET:/v1/" + resourceType + "/" + resourceId,
                resourceType, resourceId, DEVICE_FP, FACILITY_ID, WORKSPACE_ID,
                List.of("DOCTOR"), "PROVIDER", 3
        );
    }

    private static AuthzProperties buildDefaultProperties() {
        AuthzProperties props = new AuthzProperties();
        AuthzProperties.RiskThresholds thresholds = new AuthzProperties.RiskThresholds();
        thresholds.setDenyThreshold(81);
        thresholds.setStepUpTrigger(61);
        thresholds.setUnknownDeviceScore(70);
        thresholds.setNewDeviceScore(50);
        thresholds.setBlockedDeviceScore(100);
        props.setRiskThresholds(thresholds);
        props.setStepUpMethods(List.of("MFA", "BIOMETRIC", "SUPERVISOR_APPROVAL"));
        props.setStepUpWindowSeconds(300);
        props.setBreakGlassTtlMinutes(60);
        return props;
    }

    private PolicyRuleEntity buildAllowRule(String resourceType, String actorType,
                                             String role, String action, String purpose) {
        PolicyRuleEntity rule = new PolicyRuleEntity();
        rule.setTenantId(TENANT_ID);
        rule.setName("test-allow-rule");
        rule.setResourceType(resourceType);
        rule.setActorType(actorType);
        rule.setRole(role);
        rule.setAction(action);
        rule.setPurpose(purpose);
        rule.setEffect("ALLOW");
        rule.setFacilityScope(false);
        rule.setWorkspaceScope(false);
        rule.setActive(true);
        rule.setPriority(100);
        return rule;
    }

    private PolicyRuleEntity buildDenyRule(String resourceType, String actorType,
                                            String role, String action, String purpose) {
        PolicyRuleEntity rule = buildAllowRule(resourceType, actorType, role, action, purpose);
        rule.setName("test-deny-rule");
        rule.setEffect("DENY");
        rule.setPriority(50); // Higher priority (lower number)
        return rule;
    }

    /**
     * Stub risk scoring to return a safe score and stub an ALLOW policy rule
     * so the engine reaches the step under test.
     */
    private void stubHappyPathDefaults(int riskScore) {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(riskScore);

        PolicyRuleEntity allowRule = buildAllowRule("patients", null, null, null, null);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(allowRule));
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 0: Tenant validation
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("evaluate: null tenantId -> DENY with MISSING_TENANT")
    void evaluate_withMissingTenantId_denies() {
        AuthzInternalRequest request = buildRequest(
                null, "TREATMENT", "GET:/v1/patients",
                "patients", null, DEVICE_FP, FACILITY_ID, WORKSPACE_ID,
                List.of("DOCTOR"), "PROVIDER", 3
        );

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "Missing tenant must result in DENY");
        assertEquals("MISSING_TENANT", response.errorCode(),
                "Error code must be MISSING_TENANT");
        verify(decisionLogRepository).save(any(PolicyDecisionLogEntity.class));
        verify(auditPublisher).queueAuditEvent(eq(request), eq("DENY"), eq(0), eq("MISSING_TENANT"));
    }

    @Test
    @DisplayName("evaluate: VARAPI-revoked provider id -> DENY before risk scoring")
    void evaluate_withRevokedProviderPrivilege_denies() {
        AuthzInternalRequest request = defaultRequestWithProviderId("PUB123ABC");
        when(privilegeRevocationStore.isRevoked("PUB123ABC")).thenReturn(true);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("PROVIDER_PRIVILEGE_REVOKED", response.errorCode());
        verifyNoInteractions(riskScoring);
    }

    private static AuthzInternalRequest defaultRequestWithProviderId(String providerPublicId) {
        return new AuthzInternalRequest(
                TENANT_ID, ACTOR_ID, "PROVIDER", List.of("DOCTOR"), "TREATMENT",
                DEVICE_FP, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID,
                null, "GET", "/v1/patients", "GET:/v1/patients", "patients", null,
                3, "session-abc", null,
                providerPublicId, null, null, null, null, null,
                null, null
        );
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 1: Device risk scoring
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("evaluate: risk score >= 81 -> DENY with DEVICE_BLOCKED")
    void evaluate_withBlockedDevice_denies() {
        AuthzInternalRequest request = defaultRequest();
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(85);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "Blocked device (risk >= 81) must result in DENY");
        assertEquals("DEVICE_BLOCKED", response.errorCode(),
                "Error code must be DEVICE_BLOCKED");
        assertEquals(85, response.riskScore(),
                "Response must carry the actual risk score");
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 2: Purpose-of-use validation
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("evaluate: unrecognized purpose -> DENY with INVALID_PURPOSE")
    void evaluate_withInvalidPurpose_denies() {
        AuthzInternalRequest request = requestWithPurpose("BANANA");
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(10);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "Unknown purpose must result in DENY");
        assertEquals("INVALID_PURPOSE", response.errorCode(),
                "Error code must be INVALID_PURPOSE");
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 3: Break-glass
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("evaluate: BREAK_GLASS without active request -> DENY with NO_BREAK_GLASS_REQUEST")
    void evaluate_withBreakGlass_requiresActiveRequest() {
        AuthzInternalRequest request = requestWithPurpose("BREAK_GLASS");
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(10);
        when(breakGlassService.hasActiveBreakGlass(TENANT_ID, ACTOR_ID))
                .thenReturn(false);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "BREAK_GLASS without active request must DENY");
        assertEquals("NO_BREAK_GLASS_REQUEST", response.errorCode(),
                "Error code must be NO_BREAK_GLASS_REQUEST");
    }

    @Test
    @DisplayName("evaluate: BREAK_GLASS with active request but no step-up -> STEP_UP_REQUIRED")
    void evaluate_withBreakGlass_requiresStepUp() {
        AuthzInternalRequest request = requestWithPurpose("BREAK_GLASS");
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(10);
        when(breakGlassService.hasActiveBreakGlass(TENANT_ID, ACTOR_ID))
                .thenReturn(true);
        when(stepUpService.hasRecentStepUp(TENANT_ID, ACTOR_ID))
                .thenReturn(false);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.STEP_UP_REQUIRED, response.verdict(),
                "BREAK_GLASS without step-up must require STEP_UP");
        assertNotNull(response.stepUpMethods(),
                "Must return available step-up methods");
        assertTrue(response.stepUpMethods().contains("MFA"),
                "Step-up methods must include MFA");
    }

    @Test
    @DisplayName("evaluate: BREAK_GLASS with active request + step-up -> ALLOW with ELEVATED logging")
    void evaluate_withBreakGlass_allowsWithStepUp() {
        AuthzInternalRequest request = requestWithPurpose("BREAK_GLASS");
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(10);
        when(breakGlassService.hasActiveBreakGlass(TENANT_ID, ACTOR_ID))
                .thenReturn(true);
        when(stepUpService.hasRecentStepUp(TENANT_ID, ACTOR_ID))
                .thenReturn(true);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.ALLOW, response.verdict(),
                "BREAK_GLASS with valid request + step-up must ALLOW");
        assertNotNull(response.obligations(),
                "Must include obligations on ALLOW");
        assertEquals("ELEVATED", response.obligations().loggingLevel(),
                "Break-glass ALLOW must have ELEVATED logging");
        assertNotNull(response.headerMutations(),
                "ALLOW response must include header mutations");

        // Verify audit was logged
        verify(auditPublisher).queueAuditEvent(eq(request), eq("ALLOW"), eq(10), isNull(), any());
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 4: RBAC/ABAC policy evaluation
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("evaluate: matching DENY rule -> DENY immediately")
    void evaluate_withDenyRule_deniesImmediately() {
        AuthzInternalRequest request = defaultRequest();
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(10);

        PolicyRuleEntity denyRule = buildDenyRule("patients", "PROVIDER", null, "GET", null);
        when(policyCacheService.getActiveRulesForResource(TENANT_ID, "patients"))
                .thenReturn(List.of(denyRule));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "Matching DENY rule must result in immediate DENY");
        assertEquals("POLICY_DENY", response.errorCode(),
                "Error code must be POLICY_DENY");
    }

    @Test
    @DisplayName("evaluate: no matching ALLOW rule -> DENY with NO_ALLOW_RULE")
    void evaluate_withNoAllowRule_denies() {
        AuthzInternalRequest request = defaultRequest();
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(10);

        // Rule that does NOT match (different actor type)
        PolicyRuleEntity rule = buildAllowRule("patients", "ADMIN", null, null, null);
        when(policyCacheService.getActiveRulesForResource(TENANT_ID, "patients"))
                .thenReturn(List.of(rule));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "No matching ALLOW rule must DENY");
        assertEquals("NO_ALLOW_RULE", response.errorCode(),
                "Error code must be NO_ALLOW_RULE");
    }

    @Test
    @DisplayName("evaluate: no rules at all for non-SYSTEM purpose -> DENY with NO_MATCHING_RULES")
    void evaluate_withNoRulesAtAll_denies() {
        AuthzInternalRequest request = defaultRequest();
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(TENANT_ID, "patients"))
                .thenReturn(Collections.emptyList());

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "No rules at all must DENY for non-SYSTEM purpose");
        assertEquals("NO_MATCHING_RULES", response.errorCode(),
                "Error code must be NO_MATCHING_RULES");
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 5: Consent evaluation
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("evaluate: clinical resource + ALLOW rule + consent denied -> DENY")
    void evaluate_withMatchingAllowRule_checksConsent() {
        // "patients" is a clinical resource type that requires consent
        AuthzInternalRequest request = requestWithResourceId("patients", "cpid-12345");
        stubHappyPathDefaults(10);

        when(consentClient.evaluateConsent(
                eq(TENANT_ID), eq("patients"), eq("cpid-12345"),
                eq(ACTOR_ID), eq("TREATMENT")))
                .thenReturn(ConsentDecision.deny("PATIENT_OPTED_OUT"));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "Consent denied must result in DENY");
        assertEquals("CONSENT_DENIED", response.errorCode(),
                "Error code must be CONSENT_DENIED");
        verify(consentClient).evaluateConsent(
                TENANT_ID, "patients", "cpid-12345", ACTOR_ID, "TREATMENT");
    }

    @Test
    @DisplayName("evaluate: clinical resource + ALLOW rule + consent granted -> ALLOW")
    void evaluate_withMatchingAllowRule_consentGranted_allows() {
        AuthzInternalRequest request = requestWithResourceId("patients", "cpid-12345");
        stubHappyPathDefaults(10);

        when(consentClient.evaluateConsent(
                eq(TENANT_ID), eq("patients"), eq("cpid-12345"),
                eq(ACTOR_ID), eq("TREATMENT")))
                .thenReturn(ConsentDecision.permit("consent-1", List.of("read")));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.ALLOW, response.verdict(),
                "Consent granted with matching ALLOW rule must result in ALLOW");
        assertNotNull(response.headerMutations(),
                "ALLOW must include header mutations");
    }

    // ════════════════════════════════════════════════════════════════════
    // G-CZO-01: identity-assurance level (X-Assurance-Level) reaches policy
    // Keystone proof — a self-service verification upgrade changes what policy
    // sees even when the Keycloak ACR login level is unchanged.
    // ════════════════════════════════════════════════════════════════════

    /** Build a clinical-resource request with an explicit ACR loaLevel and propagated assurance level. */
    private static AuthzInternalRequest requestWithAssurance(int acrLoaLevel, String assuranceLevel) {
        return new AuthzInternalRequest(
                TENANT_ID, ACTOR_ID, "CITIZEN", List.of("CITIZEN"), "TREATMENT",
                DEVICE_FP, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID,
                null, "GET", "/v1/patients/cpid-12345", "GET:/v1/patients/cpid-12345",
                "patients", "cpid-12345",
                acrLoaLevel, "session-abc", null,
                null, null, null, null, null, assuranceLevel,
                null, null
        );
    }

    private PolicyRuleEntity buildMinLoaAllowRule(int minLoa) {
        PolicyRuleEntity rule = buildAllowRule("patients", null, null, null, null);
        rule.setConditions("{\"min_loa\":" + minLoa + "}");
        return rule;
    }

    @Test
    @DisplayName("G-CZO-01: ACR LOA1 + no assurance header + rule min_loa=3 -> DENY (temporary cannot read clinical)")
    void evaluate_lowLoa_noAssuranceHeader_belowMinLoa_denies() {
        AuthzInternalRequest request = requestWithAssurance(1, null);
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(buildMinLoaAllowRule(3)));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "Below the min_loa rule the conditioned ALLOW must not match -> DENY");
    }

    @Test
    @DisplayName("G-CZO-01: ACR LOA1 but assurance upgraded to LOA3 (header) + rule min_loa=3 -> ALLOW")
    void evaluate_assuranceUpgradeReachesPolicy_allows() {
        // ACR login level is still 1 (token unchanged); identity-assurance upgrade is propagated
        // via X-Assurance-Level=LOA3. effectiveLoa = max(1,3) = 3 satisfies min_loa=3.
        AuthzInternalRequest request = requestWithAssurance(1, "LOA3");
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(buildMinLoaAllowRule(3)));
        when(consentClient.evaluateConsent(
                eq(TENANT_ID), eq("patients"), eq("cpid-12345"), eq(ACTOR_ID), eq("TREATMENT")))
                .thenReturn(ConsentDecision.permit("consent-1", List.of("read")));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.ALLOW, response.verdict(),
                "Propagated assurance LOA3 must satisfy min_loa=3 even with ACR loaLevel=1 (closes G-CZO-01)");
    }

    @Test
    @DisplayName("G-CZO-01: bare numeric assurance header '3' is parsed and satisfies min_loa=3 -> ALLOW")
    void evaluate_bareNumericAssuranceHeader_allows() {
        AuthzInternalRequest request = requestWithAssurance(0, "3");
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(buildMinLoaAllowRule(3)));
        when(consentClient.evaluateConsent(
                eq(TENANT_ID), eq("patients"), eq("cpid-12345"), eq(ACTOR_ID), eq("TREATMENT")))
                .thenReturn(ConsentDecision.permit("consent-1", List.of("read")));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.ALLOW, response.verdict(),
                "Bare numeric X-Assurance-Level must parse to LOA rank and satisfy min_loa");
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 6: Risk-based step-up
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("evaluate: high risk + sensitive action (DELETE) + no step-up -> STEP_UP_REQUIRED")
    void evaluate_withHighRisk_sensitiveAction_requiresStepUp() {
        // DELETE action is sensitive, risk 65 >= stepUpTrigger (61)
        AuthzInternalRequest request = requestWithAction("DELETE:/v1/patients/abc", "patients");
        stubHappyPathDefaults(65);

        when(consentClient.evaluateConsent(
                eq(TENANT_ID), eq("patients"), isNull(),
                eq(ACTOR_ID), eq("TREATMENT")))
                .thenReturn(ConsentDecision.permit("consent-step-up", List.of("delete")));

        when(stepUpService.hasRecentStepUp(TENANT_ID, ACTOR_ID))
                .thenReturn(false);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.STEP_UP_REQUIRED, response.verdict(),
                "High risk + DELETE without step-up must require STEP_UP");
        assertNotNull(response.stepUpMethods(),
                "Must return step-up methods");
        assertEquals(65, response.riskScore(),
                "Response must carry the actual risk score");
    }

    @Test
    @DisplayName("evaluate: high risk + sensitive action + recent step-up -> ALLOW")
    void evaluate_withHighRisk_sensitiveAction_recentStepUp_allows() {
        AuthzInternalRequest request = requestWithAction("DELETE:/v1/patients/abc", "patients");
        stubHappyPathDefaults(65);

        when(consentClient.evaluateConsent(
                eq(TENANT_ID), eq("patients"), isNull(),
                eq(ACTOR_ID), eq("TREATMENT")))
                .thenReturn(ConsentDecision.permit("consent-step-up", List.of("delete")));

        when(stepUpService.hasRecentStepUp(TENANT_ID, ACTOR_ID))
                .thenReturn(true);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.ALLOW, response.verdict(),
                "High risk + DELETE + recent step-up must ALLOW");
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 7: Obligation computation
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("evaluate: RESEARCH purpose -> obligations include PII field masking")
    void evaluate_withResearchPurpose_masksPii() {
        AuthzInternalRequest request = requestWithPurpose("RESEARCH");
        // Use a non-clinical resource so consent is not checked
        AuthzInternalRequest nonClinicalResearch = buildRequest(
                TENANT_ID, "RESEARCH", "GET:/v1/reports",
                "reports", null, DEVICE_FP, FACILITY_ID, WORKSPACE_ID,
                List.of("RESEARCHER"), "PROVIDER", 3
        );

        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(10);
        PolicyRuleEntity allowRule = buildAllowRule("reports", null, null, null, null);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), eq("reports")))
                .thenReturn(List.of(allowRule));

        AuthzResponse response = policyEngine.evaluate(nonClinicalResearch);

        assertEquals(Verdict.ALLOW, response.verdict(),
                "RESEARCH with matching rules must ALLOW");
        assertNotNull(response.obligations(),
                "RESEARCH response must include obligations");
        assertNotNull(response.obligations().maskFields(),
                "RESEARCH obligations must have masked fields");
        assertTrue(response.obligations().maskFields().contains("name"),
                "RESEARCH must mask 'name'");
        assertTrue(response.obligations().maskFields().contains("phone"),
                "RESEARCH must mask 'phone'");
        assertTrue(response.obligations().maskFields().contains("address"),
                "RESEARCH must mask 'address'");
        assertEquals("ANONYMIZED", response.obligations().maxScope(),
                "RESEARCH must have ANONYMIZED scope");
    }

    @Test
    @DisplayName("evaluate: SYSTEM purpose with no rules -> continues to ALLOW")
    void evaluate_withSystemPurpose_allowsWithoutRules() {
        AuthzInternalRequest request = buildRequest(
                TENANT_ID, "SYSTEM", "GET:/v1/health",
                "health", null, DEVICE_FP, null, null,
                List.of("SERVICE"), "SYSTEM", 3
        );

        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), eq("health")))
                .thenReturn(Collections.emptyList());

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.ALLOW, response.verdict(),
                "SYSTEM purpose with no rules must ALLOW (service-to-service)");
    }

    // ════════════════════════════════════════════════════════════════════
    // Audit & decision logging verification
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("evaluate: every ALLOW decision persists a decision log entry")
    void evaluate_allowDecision_persistsLogEntry() {
        // Use non-clinical resource to skip consent
        AuthzInternalRequest nonClinical = buildRequest(
                TENANT_ID, "TREATMENT", "GET:/v1/facilities",
                "facilities", null, DEVICE_FP, FACILITY_ID, WORKSPACE_ID,
                List.of("DOCTOR"), "PROVIDER", 3
        );

        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(10);
        PolicyRuleEntity allowRule = buildAllowRule("facilities", null, null, null, null);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), eq("facilities")))
                .thenReturn(List.of(allowRule));

        AuthzResponse response = policyEngine.evaluate(nonClinical);

        assertEquals(Verdict.ALLOW, response.verdict());

        ArgumentCaptor<PolicyDecisionLogEntity> logCaptor =
                ArgumentCaptor.forClass(PolicyDecisionLogEntity.class);
        verify(decisionLogRepository).save(logCaptor.capture());

        PolicyDecisionLogEntity logEntry = logCaptor.getValue();
        assertEquals("ALLOW", logEntry.getVerdict(),
                "Decision log must record ALLOW verdict");
        assertEquals(TENANT_ID, logEntry.getTenantId(),
                "Decision log must record correct tenant");
        assertEquals(ACTOR_ID, logEntry.getActorId(),
                "Decision log must record correct actor");
        assertEquals(10, logEntry.getRiskScore(),
                "Decision log must record the risk score");
    }

    @Test
    @DisplayName("evaluate: every DENY decision publishes an audit event")
    void evaluate_denyDecision_publishesAudit() {
        AuthzInternalRequest request = buildRequest(
                null, "TREATMENT", "GET:/v1/patients",
                "patients", null, DEVICE_FP, FACILITY_ID, WORKSPACE_ID,
                List.of("DOCTOR"), "PROVIDER", 3
        );

        policyEngine.evaluate(request);

        verify(auditPublisher).queueAuditEvent(
                eq(request), eq("DENY"), eq(0), eq("MISSING_TENANT"));
    }

    // ════════════════════════════════════════════════════════════════════
    // Edge cases
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("evaluate: null purpose -> DENY with INVALID_PURPOSE")
    void evaluate_withNullPurpose_denies() {
        AuthzInternalRequest request = requestWithPurpose(null);
        when(riskScoring.score(eq(TENANT_ID), anyString(), eq(ACTOR_ID)))
                .thenReturn(10);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "Null purpose must DENY");
        assertEquals("INVALID_PURPOSE", response.errorCode(),
                "Error code must be INVALID_PURPOSE");
    }

    @Test
    @DisplayName("evaluate: EMERGENCY purpose skips consent for clinical resources")
    void evaluate_withEmergency_skipsConsent() {
        AuthzInternalRequest request = buildRequest(
                TENANT_ID, "EMERGENCY", "GET:/v1/patients/cpid-123",
                "patients", "cpid-123", DEVICE_FP, FACILITY_ID, WORKSPACE_ID,
                List.of("DOCTOR"), "PROVIDER", 3
        );

        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(10);
        PolicyRuleEntity allowRule = buildAllowRule("patients", null, null, null, null);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), eq("patients")))
                .thenReturn(List.of(allowRule));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.ALLOW, response.verdict(),
                "EMERGENCY must ALLOW without consent check");
        verifyNoInteractions(consentClient);
        assertEquals("ELEVATED", response.obligations().loggingLevel(),
                "EMERGENCY must have ELEVATED logging level");
    }

    @Test
    @DisplayName("evaluate: risk score exactly at deny threshold (81) -> DENY")
    void evaluate_withRiskScoreExactlyAtDenyThreshold_denies() {
        AuthzInternalRequest request = defaultRequest();
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(81);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "Risk score exactly at deny threshold must DENY");
        assertEquals("DEVICE_BLOCKED", response.errorCode());
    }

    @Test
    @DisplayName("evaluate: risk score just below deny threshold (80) -> continues evaluation")
    void evaluate_withRiskScoreJustBelowDenyThreshold_continues() {
        AuthzInternalRequest request = buildRequest(
                TENANT_ID, "TREATMENT", "GET:/v1/facilities",
                "facilities", null, DEVICE_FP, FACILITY_ID, WORKSPACE_ID,
                List.of("DOCTOR"), "PROVIDER", 3
        );

        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(80);
        PolicyRuleEntity allowRule = buildAllowRule("facilities", null, null, null, null);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), eq("facilities")))
                .thenReturn(List.of(allowRule));

        AuthzResponse response = policyEngine.evaluate(request);

        // Should not be DEVICE_BLOCKED — continues to further evaluation
        assertNotEquals("DEVICE_BLOCKED", response.errorCode(),
                "Risk 80 (below 81) must not block device");
    }
}
