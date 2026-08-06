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
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.PolicyDecisionLogRepository;
import zw.gov.mohcc.impilo.tshepo.authz.service.*;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

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

    /** Real registry: the shadow metrics are asserted, not stubbed away. */
    protected final MeterRegistry meterRegistry = new SimpleMeterRegistry();

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
        lenient().when(confidentialityPack.isEffective()).thenReturn(false);
        lenient().when(confidentialityPack.retainGrantable(anyList())).thenReturn(java.util.Set.of());
        lenient().when(confidentialityPack.stateLabel()).thenReturn("INERT(ENGINEERING_SEED,effective=false)");
        lenient().when(confidentialityPack.packId()).thenReturn("impilo.confidentiality.adolescent");
        lenient().when(confidentialityPack.version()).thenReturn("engineering-seed-1.0.0");
        policyEngine = new PolicyEngine(
                riskScoring, policyCacheService, privilegeRevocationStore, consentClient,
                stepUpService, breakGlassService, decisionLogRepository,
                auditPublisher, properties, objectMapper, visibilityEscalationService,
                delegationClient, opaDecisionClient, roleTemplateCatalog, confidentialityPack,
                decisionEnvelopeSigner,
                meterRegistry
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
                null, null, DutyContext.absent()
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
        props.setStepUpMethods(List.of("totp", "webauthn", "recovery"));
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
                null, null, DutyContext.absent()
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
    // Step 3 + Step 4.5: Break-glass (doctrine guard, then reason + step-up)
    //
    // Break-glass is ONLY for an already-authenticated, sufficiently-verified
    // PROVIDER acting in a facility context on a named patient. The Step-4.5
    // doctrine guard (evaluateBreakGlassAccess) enforces those preconditions
    // BEFORE the active-request (reason) + step-up checks widen access — it can
    // never turn an unknown user into a health worker, nor override a disputed
    // (revoked) provider identity.
    // ════════════════════════════════════════════════════════════════════

    /**
     * Build a break-glass data-access request. A fully-compliant request is a verified PROVIDER
     * (Provider ID activated, LOA3) in a facility context, targeting a named patient (X-Subject-ID).
     */
    private static AuthzInternalRequest breakGlassRequest(
            String actorType, String providerId, UUID facilityId, String subjectId,
            int acrLoa, String assuranceLevel) {
        return new AuthzInternalRequest(
                TENANT_ID, ACTOR_ID, actorType, List.of("DOCTOR"), "BREAK_GLASS",
                DEVICE_FP, CORRELATION_ID, facilityId, WORKSPACE_ID,
                null, "GET", "/v1/patients/" + (subjectId == null ? "" : subjectId),
                "GET:/v1/patients", "patients", subjectId,
                acrLoa, "session-abc", null,
                providerId, null, null, null, subjectId, assuranceLevel,
                null, null, DutyContext.absent()
        );
    }

    /** A fully doctrine-compliant break-glass request: verified provider + facility + named patient. */
    private static AuthzInternalRequest compliantBreakGlassRequest() {
        return breakGlassRequest("PROVIDER", "PUB-PROVIDER-1", FACILITY_ID, "cpid-999", 3, "LOA3");
    }

    private static AuthzInternalRequest withFreshAal2(AuthzInternalRequest request) {
        return request.withAuthenticationAssurance(new AuthenticationAssurance(
                2, List.of("pwd", "otp"), Instant.now(), Instant.now(),
                false, "session-abc", "flow-test"));
    }

    @Test
    @DisplayName("Step 4.5 break-glass: a non-provider (citizen) declaring BREAK_GLASS -> DENY (never mints a health worker)")
    void evaluate_breakGlass_nonProvider_denies() {
        AuthzInternalRequest request = breakGlassRequest("CITIZEN", null, FACILITY_ID, "cpid-999", 3, "LOA3");
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("BREAK_GLASS_REQUIRES_VERIFIED_PROVIDER", response.errorCode(),
                "Break-glass must never transform an unknown/non-provider user into a health worker");
        verifyNoInteractions(breakGlassService);
    }

    @Test
    @DisplayName("Step 4.5 break-glass: PROVIDER actor but no activated Provider ID -> DENY (verified provider)")
    void evaluate_breakGlass_noProviderId_denies() {
        AuthzInternalRequest request = breakGlassRequest("PROVIDER", null, FACILITY_ID, "cpid-999", 3, "LOA3");
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("BREAK_GLASS_REQUIRES_VERIFIED_PROVIDER", response.errorCode());
        verifyNoInteractions(breakGlassService);
    }

    @Test
    @DisplayName("Step 4.5 break-glass: verified provider but assurance below LOA2 -> DENY (sufficiently-verified)")
    void evaluate_breakGlass_lowAssurance_denies() {
        // ACR LOA1 and no assurance-header upgrade -> effectiveLoa=1 < BREAK_GLASS_MIN_LOA (2).
        AuthzInternalRequest request = breakGlassRequest("PROVIDER", "PUB-PROVIDER-1", FACILITY_ID, "cpid-999", 1, null);
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("BREAK_GLASS_REQUIRES_VERIFIED_PROVIDER", response.errorCode());
    }

    @Test
    @DisplayName("Step 4.5 break-glass: no facility context -> DENY (facility context required)")
    void evaluate_breakGlass_noFacilityContext_denies() {
        AuthzInternalRequest request = breakGlassRequest("PROVIDER", "PUB-PROVIDER-1", null, "cpid-999", 3, "LOA3");
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("BREAK_GLASS_REQUIRES_FACILITY_CONTEXT", response.errorCode());
    }

    @Test
    @DisplayName("Step 4.5 break-glass: no named patient -> DENY (patient context required; never blanket)")
    void evaluate_breakGlass_noPatientContext_denies() {
        AuthzInternalRequest request = breakGlassRequest("PROVIDER", "PUB-PROVIDER-1", FACILITY_ID, null, 3, "LOA3");
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("BREAK_GLASS_REQUIRES_PATIENT_CONTEXT", response.errorCode());
    }

    @Test
    @DisplayName("Step 4.5 break-glass: a revoked (disputed) provider identity is denied and never reaches break-glass")
    void evaluate_breakGlass_revokedProvider_denies() {
        AuthzInternalRequest request = compliantBreakGlassRequest();
        when(privilegeRevocationStore.isRevoked("PUB-PROVIDER-1")).thenReturn(true);

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("PROVIDER_PRIVILEGE_REVOKED", response.errorCode(),
                "Break-glass must never override a disputed/revoked provider identity");
        verifyNoInteractions(riskScoring, breakGlassService);
    }

    @Test
    @DisplayName("evaluate: compliant break-glass without active request -> DENY with NO_BREAK_GLASS_REQUEST")
    void evaluate_withBreakGlass_requiresActiveRequest() {
        AuthzInternalRequest request = compliantBreakGlassRequest();
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
    @DisplayName("evaluate: compliant break-glass with active request but no step-up -> STEP_UP_REQUIRED")
    void evaluate_withBreakGlass_requiresStepUp() {
        AuthzInternalRequest request = compliantBreakGlassRequest();
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(10);
        when(breakGlassService.hasActiveBreakGlass(TENANT_ID, ACTOR_ID))
                .thenReturn(true);
        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.STEP_UP_REQUIRED, response.verdict(),
                "BREAK_GLASS without step-up must require STEP_UP");
        assertNotNull(response.stepUpMethods(),
                "Must return available step-up methods");
        assertTrue(response.stepUpMethods().contains("totp"),
                "Step-up methods must include native TOTP");
    }

    @Test
    @DisplayName("evaluate: compliant break-glass with active request + step-up -> ALLOW with ELEVATED logging")
    void evaluate_withBreakGlass_allowsWithStepUp() {
        AuthzInternalRequest request = withFreshAal2(compliantBreakGlassRequest());
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID)))
                .thenReturn(10);
        when(breakGlassService.hasActiveBreakGlass(TENANT_ID, ACTOR_ID))
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
    // D2: the signed decision envelope
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("D2: break-glass ALLOW carries the envelope too — every allow path, not the common one")
    void evaluate_breakGlassAllow_alsoCarriesTheEnvelope() {
        when(decisionEnvelopeSigner.sign(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(java.util.Optional.of("header.payload.signature"));
        AuthzInternalRequest request = withFreshAal2(compliantBreakGlassRequest());
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(breakGlassService.hasActiveBreakGlass(TENANT_ID, ACTOR_ID)).thenReturn(true);
        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.ALLOW, response.verdict());
        assertEquals("header.payload.signature",
                response.headerMutations().get(TrustHeaders.DECISION_ENVELOPE),
                "Break-glass is the allow path most worth proving, and it builds its headers "
                        + "through a separate call site from the ordinary allow");
    }

    @Test
    @DisplayName("D2: the envelope commits to the obligations string that is actually sent upstream")
    void evaluate_envelopeDigestsTheHeaderItShipsBeside() {
        org.mockito.ArgumentCaptor<String> obligationsSent = org.mockito.ArgumentCaptor.forClass(String.class);
        when(decisionEnvelopeSigner.sign(any(), any(), any(), any(), any(), any(), obligationsSent.capture()))
                .thenReturn(java.util.Optional.of("header.payload.signature"));
        AuthzInternalRequest request = withFreshAal2(compliantBreakGlassRequest());
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(breakGlassService.hasActiveBreakGlass(TENANT_ID, ACTOR_ID)).thenReturn(true);
        AuthzResponse response = policyEngine.evaluate(request);

        // If these two ever diverge, a caller could keep the genuine envelope and widen the
        // obligations beside it, which is precisely what the digest exists to prevent.
        assertEquals(response.headerMutations().get(TrustHeaders.OBLIGATIONS),
                obligationsSent.getValue(),
                "The digested string and the shipped header must be the same string");
    }

    @Test
    @DisplayName("D2: a PDP that cannot sign still allows — no envelope header, no denial")
    void evaluate_whenSigningUnavailable_allowsWithoutTheHeader() {
        when(decisionEnvelopeSigner.sign(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(java.util.Optional.empty());
        AuthzInternalRequest request = withFreshAal2(compliantBreakGlassRequest());
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(breakGlassService.hasActiveBreakGlass(TENANT_ID, ACTOR_ID)).thenReturn(true);
        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.ALLOW, response.verdict(),
                "A keys-service outage must not become an estate-wide denial");
        assertFalse(response.headerMutations().containsKey(TrustHeaders.DECISION_ENVELOPE),
                "Absent rather than empty: an empty envelope would look like a malformed one");
    }

    // ════════════════════════════════════════════════════════════════════
    // V056: break-glass GOVERNANCE surface (management endpoints at the gateway)
    //
    // Mirrors the V056 seed: the /v1/break-glass request/review endpoints are
    // granted only to a verified provider (create) or a supervisor (review),
    // pinned by path_contains + min_loa. "Never mint a health worker" is enforced
    // by FAIL-CLOSED default-deny (no ALLOW rule grants a citizen the surface) —
    // NOT by a DENY row, because the engine ignores a DENY rule's conditions and a
    // path-scoped citizen DENY would be impossible / over-broad.
    // ════════════════════════════════════════════════════════════════════

    /** A break-glass MANAGEMENT-endpoint request (OPERATIONS purpose, not BREAK_GLASS). */
    private static AuthzInternalRequest breakGlassMgmtRequest(
            String path, String httpMethod, List<String> roles, String actorType, String assuranceLevel) {
        String resourceType = path.substring(path.lastIndexOf('/') + 1);
        return new AuthzInternalRequest(
                TENANT_ID, ACTOR_ID, actorType, roles, "OPERATIONS",
                DEVICE_FP, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID,
                null, httpMethod, path, httpMethod + ":" + path, resourceType, null,
                2, "session-abc", null,
                null, null, null, null, null, assuranceLevel,
                null, null, DutyContext.absent()
        );
    }

    /** The V056 ALLOW rule: raise a break-glass request (verified provider, pinned + min_loa 2). */
    private PolicyRuleEntity breakGlassRequestAllowRule() {
        PolicyRuleEntity rule = buildAllowRule("break-glass", "PROVIDER", null, "POST", null);
        rule.setName("break-glass-request-provider");
        rule.setConditions("{\"path_contains\": \"/v1/break-glass\", \"min_loa\": 2}");
        rule.setPriority(40);
        return rule;
    }

    /** The V056 ALLOW rule: supervisor lists the review queue (pinned to /v1/break-glass/review). */
    private PolicyRuleEntity breakGlassReviewAllowRule() {
        PolicyRuleEntity rule = buildAllowRule("review", "PROVIDER", "DISTRICT_SUPERVISOR", "GET", null);
        rule.setName("break-glass-review-list-supervisor");
        rule.setConditions("{\"path_contains\": \"/v1/break-glass/review\", \"min_loa\": 2}");
        rule.setPriority(40);
        return rule;
    }

    @Test
    @DisplayName("V056: verified PROVIDER (LOA2) POSTing /v1/break-glass -> ALLOW (raise request)")
    void evaluate_breakGlassMgmt_providerRaise_allows() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(breakGlassRequestAllowRule()));

        AuthzInternalRequest req = breakGlassMgmtRequest(
                "/v1/break-glass", "POST", List.of("DOCTOR"), "PROVIDER", "LOA2");

        assertEquals(Verdict.ALLOW, policyEngine.evaluate(req).verdict(),
                "A verified provider at LOA2 must be allowed to raise a break-glass request");
    }

    @Test
    @DisplayName("V056: CITIZEN reaching the break-glass surface -> DENY (fail-closed default; never minted)")
    void evaluate_breakGlassMgmt_citizen_denies() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        // Only the provider/supervisor ALLOW rules exist; a citizen matches none -> NO_ALLOW_RULE.
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(breakGlassRequestAllowRule(), breakGlassReviewAllowRule()));

        AuthzInternalRequest req = breakGlassMgmtRequest(
                "/v1/break-glass", "POST", List.of("CITIZEN"), "CITIZEN", "LOA2");

        AuthzResponse resp = policyEngine.evaluate(req);
        assertEquals(Verdict.DENY, resp.verdict(),
                "A citizen must never be granted the break-glass management surface");
        assertEquals("NO_ALLOW_RULE", resp.errorCode(),
                "Default-deny (no matching ALLOW) — not an over-broad DENY row");
    }

    @Test
    @DisplayName("V056: DISTRICT_SUPERVISOR listing /v1/break-glass/review -> ALLOW (retrospective review)")
    void evaluate_breakGlassMgmt_supervisorReview_allows() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(breakGlassReviewAllowRule()));

        AuthzInternalRequest req = breakGlassMgmtRequest(
                "/v1/break-glass/review", "GET", List.of("DISTRICT_SUPERVISOR"), "PROVIDER", "LOA2");

        assertEquals(Verdict.ALLOW, policyEngine.evaluate(req).verdict(),
                "A DISTRICT_SUPERVISOR must be allowed to review the pending break-glass queue");
    }

    @Test
    @DisplayName("V056 collision-safety: the review ALLOW does NOT grant another service's /review endpoint")
    void evaluate_breakGlassMgmt_reviewCollision_denies() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        // Same supervisor rule (pinned to /v1/break-glass/review) must not grant a DIFFERENT service's
        // endpoint that merely shares the generic last segment 'review'.
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(breakGlassReviewAllowRule()));

        AuthzInternalRequest req = breakGlassMgmtRequest(
                "/v1/quality/case/review", "GET", List.of("DISTRICT_SUPERVISOR"), "PROVIDER", "LOA2");

        AuthzResponse resp = policyEngine.evaluate(req);
        assertEquals(Verdict.DENY, resp.verdict(),
                "A colliding 'review' segment on a non-break-glass path must not be granted here");
        assertEquals("NO_ALLOW_RULE", resp.errorCode());
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
    // Step 4b: path_contains condition (resource-type collision isolation)
    // ════════════════════════════════════════════════════════════════════

    /** Builds an AuthzInternalRequest with a caller-supplied path (mirrors buildRequest defaults). */
    private static AuthzInternalRequest buildRequestWithPath(
            String path, String resourceType, String action, List<String> roles) {
        return new AuthzInternalRequest(
                TENANT_ID, ACTOR_ID, "PROVIDER", roles, "TREATMENT",
                DEVICE_FP, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID,
                null, "POST", path, action, resourceType, null,
                3, "session-abc", null,
                null, null, null, null, null, null,
                null, null, DutyContext.absent()
        );
    }

    @Test
    @DisplayName("identity headers are emitted even when the PDP has no value — an empty header displaces a forged one")
    void allowEmitsIdentityHeadersUnconditionally() {
        // allowed_upstream_headers copies what the PDP sets ON TOP OF the client's copy, and
        // that overwrite is the anti-impersonation control. A header the PDP declines to emit
        // therefore leaves the CLIENT's value travelling onward — so a caller could assert
        // x-provider-id or x-subject-id and keep it precisely because the PDP had none.
        //
        // The route-level strip that used to cover this could not: Envoy removes route headers
        // in the router, after the filter chain, so it deleted what ext_authz had restored and
        // took nine shell endpoints from 200 to 400. Emitting unconditionally is what lets that
        // strip go away.
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity rule = buildAllowRule("patients", "PROVIDER", "DOCTOR", "GET", "TREATMENT");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));
        when(consentClient.evaluateConsent(
                eq(TENANT_ID), eq("patients"), any(), eq(ACTOR_ID), eq("TREATMENT")))
                .thenReturn(ConsentDecision.permit("consent-1", List.of("read")));

        // defaultRequest() carries no provider, subject or assurance level.
        AuthzResponse response = policyEngine.evaluate(defaultRequest());

        assertEquals(Verdict.ALLOW, response.verdict());
        for (String header : List.of(TrustHeaders.PROVIDER_ID, TrustHeaders.SUBJECT_ID,
                                     TrustHeaders.ASSURANCE_LEVEL, TrustHeaders.TENANT_ID,
                                     TrustHeaders.ACTOR_ID, TrustHeaders.ACTOR_TYPE)) {
            assertTrue(response.headerMutations().containsKey(header),
                    header + " must be PRESENT even when empty: an omitted header leaves the "
                            + "client's forged value in place, which is the hole this closes");
        }
        assertEquals("", response.headerMutations().get(TrustHeaders.PROVIDER_ID),
                "no provider on this request, so the emitted value clears rather than asserts");
        assertEquals("", response.headerMutations().get(TrustHeaders.SUBJECT_ID));
    }

    private PolicyRuleEntity buildAllowRuleWithConditions(
            String resourceType, String role, String action, String conditionsJson) {
        PolicyRuleEntity rule = buildAllowRule(resourceType, "PROVIDER", role, action, "TREATMENT");
        rule.setConditions(conditionsJson);
        return rule;
    }

    @Test
    @DisplayName("path_contains: rule pinned to /cadre/decision ALLOWS the cadre endpoint")
    void evaluate_pathContains_matchingPath_allows() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        // resource_type 'decision' is generic (collides across services); the rule pins it to /cadre/decision.
        PolicyRuleEntity rule = buildAllowRuleWithConditions(
                "decision", "CLINICIAN", "POST", "{\"path_contains\": \"/cadre/decision\"}");
        when(policyCacheService.getActiveRulesForResource(TENANT_ID, "decision"))
                .thenReturn(List.of(rule));

        AuthzInternalRequest request = buildRequestWithPath(
                "/v1/cadre/decision", "decision", "POST:/v1/cadre/decision", List.of("CLINICIAN"));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.ALLOW, response.verdict(),
                "A CLINICIAN POSTing to /v1/cadre/decision must be allowed by the pinned rule");
    }

    @Test
    @DisplayName("path_contains: same resource_type on a DIFFERENT service path is NOT over-granted")
    void evaluate_pathContains_collidingSegmentDifferentPath_denies() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        // The SAME cadre rule (pinned to /cadre/decision) must not grant another service's
        // endpoint that merely shares the last path segment 'decision'.
        PolicyRuleEntity rule = buildAllowRuleWithConditions(
                "decision", "CLINICIAN", "POST", "{\"path_contains\": \"/cadre/decision\"}");
        when(policyCacheService.getActiveRulesForResource(TENANT_ID, "decision"))
                .thenReturn(List.of(rule));

        AuthzInternalRequest request = buildRequestWithPath(
                "/v1/governance/waiver/decision", "decision",
                "POST:/v1/governance/waiver/decision", List.of("CLINICIAN"));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "A colliding 'decision' segment on a non-cadre path must NOT be granted (no cross-service over-grant)");
        assertEquals("NO_ALLOW_RULE", response.errorCode());
    }

    @Test
    @DisplayName("conditional DENY fires ONLY on its matching path — regression for DENY-ignores-conditions")
    void evaluate_conditionalDeny_nonMatchingPath_isNotDenied() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        // A DENY pinned to /internal/v1/assets by path_contains, plus a general ALLOW for the role.
        PolicyRuleEntity denyRule = buildDenyRule(null, null, "CLINICIAN", null, null);
        denyRule.setConditions("{\"path_contains\": \"/internal/v1/assets\"}");
        PolicyRuleEntity allowRule = buildAllowRule(null, null, "CLINICIAN", null, null);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(denyRule, allowRule));

        // An UNRELATED path must NOT be denied by the assets-scoped DENY (the bug denied the whole role).
        AuthzInternalRequest request = buildRequestWithPath(
                "/v1/wellness/feed", "feed", "GET:/v1/wellness/feed", List.of("CLINICIAN"));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.ALLOW, response.verdict(),
                "A conditional DENY (path_contains /internal/v1/assets) must not deny an unrelated path");
    }

    @Test
    @DisplayName("conditional DENY still fires on its own pinned path")
    void evaluate_conditionalDeny_matchingPath_denies() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity denyRule = buildDenyRule(null, null, "CLINICIAN", null, null);
        denyRule.setConditions("{\"path_contains\": \"/internal/v1/assets\"}");
        PolicyRuleEntity allowRule = buildAllowRule(null, null, "CLINICIAN", null, null);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(denyRule, allowRule));

        AuthzInternalRequest request = buildRequestWithPath(
                "/internal/v1/assets/list", "assets", "GET:/internal/v1/assets/list", List.of("CLINICIAN"));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(), "The conditional DENY must still fire on its pinned path");
        assertEquals("POLICY_DENY", response.errorCode());
    }

    // ════════════════════════════════════════════════════════════════════
    // Unknown condition key — the "differently active" root fix.
    // A key evaluateConditions does not implement is a NON-MATCH, not the old silent fall-through
    // to `return true`. Both tests discriminate old from new: on the old engine the unknown key was
    // ignored, so the ALLOW granted and the DENY fired; the fix flips both to a non-match.
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("unknown condition key on an ALLOW grants nothing (fail-closed, not silently ignored)")
    void evaluate_unknownConditionKey_onAllow_isNotGranted() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        // 'deny_operational_lanes' is not a key the engine implements. The coarse tuple (PROVIDER /
        // CLINICIAN / POST / TREATMENT / decision) matches the request, so the ONLY thing that can
        // stop the grant is the unknown key. The old engine ignored it and granted; the fix denies.
        PolicyRuleEntity rule = buildAllowRuleWithConditions(
                "decision", "CLINICIAN", "POST", "{\"deny_operational_lanes\": true}");
        when(policyCacheService.getActiveRulesForResource(TENANT_ID, "decision"))
                .thenReturn(List.of(rule));

        AuthzInternalRequest request = buildRequestWithPath(
                "/v1/cadre/decision", "decision", "POST:/v1/cadre/decision", List.of("CLINICIAN"));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "An ALLOW carrying an unimplemented condition key must not grant — a rule the engine "
                + "cannot honour is a non-match, not a silent pass");
        assertEquals("NO_ALLOW_RULE", response.errorCode());
    }

    @Test
    @DisplayName("unknown condition key on a DENY denies nothing (fail-narrow — cannot swallow the ALLOWs beside it)")
    void evaluate_unknownConditionKey_onDeny_doesNotDeny() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        // rom-hpa-no-operational-access's original shape: a DENY reading "deny the operational lanes"
        // that, with the key unimplemented, the old engine evaluated as "deny everything" — and
        // DENY-wins swallowed the ALLOW seeded beside it. The fix makes the unknown-key DENY a
        // non-match, so the ALLOW for the role stands.
        PolicyRuleEntity denyRule = buildDenyRule(null, null, "CLINICIAN", null, null);
        denyRule.setConditions("{\"deny_operational_lanes\": true}");
        PolicyRuleEntity allowRule = buildAllowRule(null, null, "CLINICIAN", null, null);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(denyRule, allowRule));

        AuthzInternalRequest request = buildRequestWithPath(
                "/v1/wellness/feed", "feed", "GET:/v1/wellness/feed", List.of("CLINICIAN"));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.ALLOW, response.verdict(),
                "A DENY carrying an unimplemented condition key must not fire — otherwise it denies "
                + "more than it reads and DENY-wins swallows the ALLOWs seeded beside it");
    }

    // ── First-class WORK_CONTEXT dimensions (Phase D) ──────────────────────
    private static AuthzInternalRequest dimRequest(String path, String resourceType, String action,
            List<String> roles, String departmentId, String providerId, String workflowState,
            DutyContext duty) {
        return new AuthzInternalRequest(
                TENANT_ID, ACTOR_ID, "PROVIDER", roles, "TREATMENT",
                DEVICE_FP, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID,
                null, "POST", path, action, resourceType, null,
                3, "session-abc", null,
                providerId, departmentId, null, null, null, null,
                null, workflowState, duty);
    }

    @Test
    @DisplayName("workflow-state: blocked_workflow_states denies an amend on a DISCHARGED transaction")
    void evaluate_workflowState_blocked_denies() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity rule = buildAllowRuleWithConditions("note", "CLINICIAN", "POST",
                "{\"path_contains\": \"/x/note\", \"blocked_workflow_states\": [\"DISCHARGED\"]}");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        AuthzResponse active = policyEngine.evaluate(dimRequest("/x/note", "note", "POST:/x/note",
                List.of("CLINICIAN"), null, null, "ACTIVE", DutyContext.absent()));
        assertEquals(Verdict.ALLOW, active.verdict(), "amend on an ACTIVE transaction is allowed");

        AuthzResponse discharged = policyEngine.evaluate(dimRequest("/x/note", "note", "POST:/x/note",
                List.of("CLINICIAN"), null, null, "DISCHARGED", DutyContext.absent()));
        assertEquals(Verdict.DENY, discharged.verdict(), "amend on a DISCHARGED transaction is blocked");
    }

    // ── jurisdiction (RB-2c) ────────────────────────────────────────────────
    //
    // A regulator's authority is bounded by WHERE as well as by which organisation. Before this
    // dimension existed, an inspector appointed for one province held a session indistinguishable
    // from a national one — and the appointment's jurisdiction, though read by the BFF, was never
    // put on the token at all.

    private static DutyContext regulatoryDuty(String jurisdiction) {
        return new DutyContext(true, true, "WORK_CONTEXT", ACTOR_ID,
                null, null, null, null, null, "org-1", null, "INSPECTOR", null, jurisdiction,
                "REGULATORY_OPERATIONS", "NONE", null, null);
    }

    @Test
    @DisplayName("jurisdiction: allowed_jurisdictions gates to the appointed jurisdiction")
    void evaluate_jurisdiction_gate() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity rule = buildAllowRuleWithConditions("insp", "INSPECTOR", "POST",
                "{\"path_contains\": \"/x/insp\", \"allowed_jurisdictions\": [\"HARARE\"]}");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        AuthzResponse ok = policyEngine.evaluate(dimRequest("/x/insp", "insp", "POST:/x/insp",
                List.of("INSPECTOR"), null, null, null, regulatoryDuty("HARARE")));
        assertEquals(Verdict.ALLOW, ok.verdict(), "the appointed jurisdiction is allowed");

        AuthzResponse elsewhere = policyEngine.evaluate(dimRequest("/x/insp", "insp", "POST:/x/insp",
                List.of("INSPECTOR"), null, null, null, regulatoryDuty("BULAWAYO")));
        assertEquals(Verdict.DENY, elsewhere.verdict(),
                "an inspector appointed elsewhere may not act in this jurisdiction");
    }

    @Test
    @DisplayName("jurisdiction: NATIONAL duty satisfies a province-scoped rule")
    void evaluate_jurisdiction_nationalIsAWildcardOnTheDutySide() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity rule = buildAllowRuleWithConditions("insp", "INSPECTOR", "POST",
                "{\"path_contains\": \"/x/insp\", \"allowed_jurisdictions\": [\"HARARE\"]}");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        AuthzResponse national = policyEngine.evaluate(dimRequest("/x/insp", "insp", "POST:/x/insp",
                List.of("INSPECTOR"), null, null, null, regulatoryDuty("NATIONAL")));
        assertEquals(Verdict.ALLOW, national.verdict(),
                "a nationally-appointed officer covers any province");
    }

    /**
     * The dimension must fail closed. A caller with no duty token has not proven any jurisdiction,
     * and treating "unknown" as "anywhere" would make the condition decorative — the same defect
     * as an unimplemented key, reached by a different route.
     */
    @Test
    @DisplayName("jurisdiction: no duty token proves no jurisdiction, so the rule does not match")
    void evaluate_jurisdiction_absentDutyIsDenied() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity rule = buildAllowRuleWithConditions("insp", "INSPECTOR", "POST",
                "{\"path_contains\": \"/x/insp\", \"allowed_jurisdictions\": [\"HARARE\"]}");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        AuthzResponse noToken = policyEngine.evaluate(dimRequest("/x/insp", "insp", "POST:/x/insp",
                List.of("INSPECTOR"), null, null, null, DutyContext.absent()));
        assertEquals(Verdict.DENY, noToken.verdict(),
                "an unproven jurisdiction is not a satisfied jurisdiction");
    }

    /**
     * A revoked token must not carry authority forward. Without {@code usable()} gating, the claims
     * of a token the trust plane has already torn down would still satisfy the condition.
     */
    @Test
    @DisplayName("jurisdiction: a revoked duty token proves nothing")
    void evaluate_jurisdiction_revokedDutyIsDenied() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity rule = buildAllowRuleWithConditions("insp", "INSPECTOR", "POST",
                "{\"path_contains\": \"/x/insp\", \"allowed_jurisdictions\": [\"HARARE\"]}");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        AuthzResponse revoked = policyEngine.evaluate(dimRequest("/x/insp", "insp", "POST:/x/insp",
                List.of("INSPECTOR"), null, null, null, DutyContext.revoked()));
        assertEquals(Verdict.DENY, revoked.verdict(), "a revoked token carries no jurisdiction");
    }

    @Test
    @DisplayName("department: allowed_departments gates to the matching department")
    void evaluate_department_gate() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity rule = buildAllowRuleWithConditions("dept", "CLINICIAN", "POST",
                "{\"path_contains\": \"/x/dept\", \"allowed_departments\": [\"cardiology\"]}");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        AuthzResponse ok = policyEngine.evaluate(dimRequest("/x/dept", "dept", "POST:/x/dept",
                List.of("CLINICIAN"), "cardiology", null, null, DutyContext.absent()));
        assertEquals(Verdict.ALLOW, ok.verdict(), "cardiology department is allowed");

        AuthzResponse wrong = policyEngine.evaluate(dimRequest("/x/dept", "dept", "POST:/x/dept",
                List.of("CLINICIAN"), "oncology", null, null, DutyContext.absent()));
        assertEquals(Verdict.DENY, wrong.verdict(), "a different department is denied");
    }

    @Test
    @DisplayName("provider-id: requires_provider_id denies when no provider id is attached")
    void evaluate_requiresProviderId() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity rule = buildAllowRuleWithConditions("rx", "CLINICIAN", "POST",
                "{\"path_contains\": \"/x/rx\", \"requires_provider_id\": true}");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        AuthzResponse with = policyEngine.evaluate(dimRequest("/x/rx", "rx", "POST:/x/rx",
                List.of("CLINICIAN"), null, "PROV-PUBLIC-1", null, DutyContext.absent()));
        assertEquals(Verdict.ALLOW, with.verdict(), "provider id present → allowed");

        AuthzResponse without = policyEngine.evaluate(dimRequest("/x/rx", "rx", "POST:/x/rx",
                List.of("CLINICIAN"), null, null, null, DutyContext.absent()));
        assertEquals(Verdict.DENY, without.verdict(), "no provider id → denied");
    }

    @Test
    @DisplayName("role-template: a token carrying a template id matches a rule via its catalog canonical role")
    void evaluate_roleTemplate_foldsCanonicalRole() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(roleTemplateCatalog.resolve(TENANT_ID, "NURSE_ONCOLOGY_SNR"))
                .thenReturn(List.of("CLINICIAN"));
        PolicyRuleEntity rule = buildAllowRule("note", null, "CLINICIAN", null, null);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        // The Keycloak claim carries NO CLINICIAN role; the duty token proves NURSE_ONCOLOGY_SNR,
        // which the catalog maps to CLINICIAN. Facility/actor match so the token is trusted.
        DutyContext duty = new DutyContext(true, true, "WORK_CONTEXT", ACTOR_ID,
                FACILITY_ID.toString(), null, null, null, null, null, null, "NURSE_ONCOLOGY_SNR", null, null,
                "CLINICAL_CARE", "IDENTIFIED", null, null);
        AuthzResponse resp = policyEngine.evaluate(dimRequest("/x/note", "note", "POST:/x/note",
                List.of(), null, null, null, duty));

        assertEquals(Verdict.ALLOW, resp.verdict(),
                "duty role NURSE_ONCOLOGY_SNR → canonical CLINICIAN → matches the rule");
    }

    // ── WorkMode-gated role folding (Phase B) ──────────────────────────────
    // The property that makes "management/support/regulatory mode grants no
    // patient access" true: the canonical CLINICIAN role is folded from a
    // matched duty token only when the mode's clinicalDataAccess is
    // IDENTIFIED. A management-mode session still proves WARD_CHARGE_NURSE
    // (the raw duty fact), but never CLINICIAN (the canonical grant every
    // clinical policy_rule matches on).

    @Test
    @DisplayName("WorkMode: DEPARTMENT_MANAGEMENT does NOT fold the canonical CLINICIAN role")
    void evaluate_managementMode_doesNotFoldClinicianRole() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(roleTemplateCatalog.resolve(TENANT_ID, "WARD_CHARGE_NURSE"))
                .thenReturn(List.of("CLINICIAN"));
        PolicyRuleEntity rule = buildAllowRule("note", null, "CLINICIAN", null, null);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        DutyContext duty = new DutyContext(true, true, "WORK_CONTEXT", ACTOR_ID,
                FACILITY_ID.toString(), null, null, null, null, null, null, "WARD_CHARGE_NURSE", null, null,
                "DEPARTMENT_MANAGEMENT", "AGGREGATE", null, null);
        AuthzResponse resp = policyEngine.evaluate(dimRequest("/x/note", "note", "POST:/x/note",
                List.of(), null, null, null, duty));

        assertEquals(Verdict.DENY, resp.verdict(),
                "DEPARTMENT_MANAGEMENT mode (AGGREGATE clinical access) must not fold CLINICIAN, "
                        + "so a rule keyed on CLINICIAN must not match");
    }

    @Test
    @DisplayName("WorkMode: CLINICAL_CARE (IDENTIFIED) still folds the canonical CLINICIAN role")
    void evaluate_clinicalMode_stillFoldsClinicianRole() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(roleTemplateCatalog.resolve(TENANT_ID, "WARD_CHARGE_NURSE"))
                .thenReturn(List.of("CLINICIAN"));
        PolicyRuleEntity rule = buildAllowRule("note", null, "CLINICIAN", null, null);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        DutyContext duty = new DutyContext(true, true, "WORK_CONTEXT", ACTOR_ID,
                FACILITY_ID.toString(), null, null, null, null, null, null, "WARD_CHARGE_NURSE", null, null,
                "CLINICAL_CARE", "IDENTIFIED", null, null);
        AuthzResponse resp = policyEngine.evaluate(dimRequest("/x/note", "note", "POST:/x/note",
                List.of(), null, null, null, duty));

        assertEquals(Verdict.ALLOW, resp.verdict(), "CLINICAL_CARE mode still folds CLINICIAN");
    }

    @Test
    @DisplayName("WorkMode: allowed_modes gates to the duty's mode")
    void evaluate_allowedModes_gate() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity rule = buildAllowRuleWithConditions("mode-gate", null, "POST",
                "{\"path_contains\": \"/x/mode-gate\", \"allowed_modes\": [\"FACILITY_MANAGEMENT\"]}");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        DutyContext facilityMgmt = new DutyContext(true, true, "WORK_CONTEXT", ACTOR_ID,
                FACILITY_ID.toString(), null, null, null, null, null, null, "FACILITY_ADMINISTRATOR", null, null,
                "FACILITY_MANAGEMENT", "AGGREGATE", null, null);
        AuthzResponse allowed = policyEngine.evaluate(dimRequest("/x/mode-gate", "mode-gate", "POST:/x/mode-gate",
                List.of(), null, null, null, facilityMgmt));
        assertEquals(Verdict.ALLOW, allowed.verdict(), "matching mode is allowed");

        DutyContext clinical = new DutyContext(true, true, "WORK_CONTEXT", ACTOR_ID,
                FACILITY_ID.toString(), null, null, null, null, null, null, "MEDICAL_OFFICER", null, null,
                "CLINICAL_CARE", "IDENTIFIED", null, null);
        AuthzResponse denied = policyEngine.evaluate(dimRequest("/x/mode-gate", "mode-gate", "POST:/x/mode-gate",
                List.of(), null, null, null, clinical));
        assertEquals(Verdict.DENY, denied.verdict(), "a different mode does not match allowed_modes");

        AuthzResponse noToken = policyEngine.evaluate(dimRequest("/x/mode-gate", "mode-gate", "POST:/x/mode-gate",
                List.of(), null, null, null, DutyContext.absent()));
        assertEquals(Verdict.DENY, noToken.verdict(), "no usable duty token fails closed on allowed_modes");
    }

    @Test
    @DisplayName("WorkMode: blocked_modes denies the listed mode, never denies an absent mode")
    void evaluate_blockedModes_gate() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity rule = buildAllowRuleWithConditions("mode-block", null, "POST",
                "{\"path_contains\": \"/x/mode-block\", \"blocked_modes\": [\"TECHNICAL_SUPPORT\"]}");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        DutyContext support = new DutyContext(true, true, "WORK_CONTEXT", ACTOR_ID,
                null, null, null, null, null, "org-1", null, "SUPPORT_OFFICER", null, null,
                "TECHNICAL_SUPPORT", "NONE", null, null);
        AuthzResponse denied = policyEngine.evaluate(dimRequest("/x/mode-block", "mode-block", "POST:/x/mode-block",
                List.of(), null, null, null, support));
        assertEquals(Verdict.DENY, denied.verdict(), "the blocked mode does not match");

        AuthzResponse noToken = policyEngine.evaluate(dimRequest("/x/mode-block", "mode-block", "POST:/x/mode-block",
                List.of(), null, null, null, DutyContext.absent()));
        assertEquals(Verdict.ALLOW, noToken.verdict(), "an absent mode is NOT treated as blocked (non-duty traffic unaffected)");
    }

    @Test
    @DisplayName("WorkMode: requires_identified_clinical_access rejects AGGREGATE/NONE duty modes")
    void evaluate_requiresIdentifiedClinicalAccess_gate() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity rule = buildAllowRuleWithConditions("needs-clinical", null, "GET",
                "{\"path_contains\": \"/x/needs-clinical\", \"requires_identified_clinical_access\": true}");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        DutyContext clinical = new DutyContext(true, true, "WORK_CONTEXT", ACTOR_ID,
                FACILITY_ID.toString(), null, null, null, null, null, null, "MEDICAL_OFFICER", null, null,
                "CLINICAL_CARE", "IDENTIFIED", null, null);
        AuthzResponse allowed = policyEngine.evaluate(dimRequest("/x/needs-clinical", "needs-clinical",
                "GET:/x/needs-clinical", List.of(), null, null, null, clinical));
        assertEquals(Verdict.ALLOW, allowed.verdict());

        DutyContext management = new DutyContext(true, true, "WORK_CONTEXT", ACTOR_ID,
                FACILITY_ID.toString(), null, null, null, null, null, null, "FACILITY_ADMINISTRATOR", null, null,
                "FACILITY_MANAGEMENT", "AGGREGATE", null, null);
        AuthzResponse denied = policyEngine.evaluate(dimRequest("/x/needs-clinical", "needs-clinical",
                "GET:/x/needs-clinical", List.of(), null, null, null, management));
        assertEquals(Verdict.DENY, denied.verdict(), "AGGREGATE clinical access does not satisfy the requirement");
    }

    @Test
    @DisplayName("path_contains: query-string smuggling of the pinned substring is NOT granted")
    void evaluate_pathContains_querySmuggling_denies() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity rule = buildAllowRuleWithConditions(
                "decision", "CLINICIAN", "POST", "{\"path_contains\": \"/cadre/decision\"}");
        when(policyCacheService.getActiveRulesForResource(TENANT_ID, "decision"))
                .thenReturn(List.of(rule));

        // A non-cadre /decision endpoint with the pinned substring smuggled into the QUERY string.
        // The path is normalised (query stripped) before matching, so this must still DENY.
        AuthzInternalRequest request = buildRequestWithPath(
                "/v1/governance/waiver/decision?x=/cadre/decision", "decision",
                "POST:/v1/governance/waiver/decision", List.of("CLINICIAN"));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "Query-smuggled path_contains substring must not grant an unrelated endpoint");
        assertEquals("NO_ALLOW_RULE", response.errorCode());
    }

    @Test
    @DisplayName("path_contains: percent-encoded query delimiter (%3F) smuggling is NOT granted")
    void evaluate_pathContains_encodedDelimiterSmuggling_denies() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity rule = buildAllowRuleWithConditions(
                "decision", "CLINICIAN", "POST", "{\"path_contains\": \"/cadre/decision\"}");
        when(policyCacheService.getActiveRulesForResource(TENANT_ID, "decision"))
                .thenReturn(List.of(rule));

        // The pinned substring is smuggled behind a percent-ENCODED '?' (%3F). Envoy delivers
        // :path undecoded, so the PDP strips at %3F too — this must still DENY.
        AuthzInternalRequest request = buildRequestWithPath(
                "/v1/governance/waiver/decision%3F=/cadre/decision", "decision",
                "POST:/v1/governance/waiver/decision", List.of("CLINICIAN"));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "Encoded-delimiter smuggling must not grant an unrelated endpoint");
        assertEquals("NO_ALLOW_RULE", response.errorCode());
    }

    @Test
    @DisplayName("path_contains: a longer same-prefix route (/care-plans-export) is NOT granted")
    void evaluate_pathContains_segmentBoundary_denies() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity rule = buildAllowRuleWithConditions(
                "care-plans-export", "CLINICIAN", "POST", "{\"path_contains\": \"/care-plans\"}");
        when(policyCacheService.getActiveRulesForResource(TENANT_ID, "care-plans-export"))
                .thenReturn(List.of(rule));

        // resource_type 'care-plans-export' shares the /care-plans prefix but is a different segment;
        // segment-bounded matching must NOT treat the /care-plans pin as satisfied.
        AuthzInternalRequest request = buildRequestWithPath(
                "/v1/care-plans-export", "care-plans-export",
                "POST:/v1/care-plans-export", List.of("CLINICIAN"));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.DENY, response.verdict(),
                "A /care-plans pin must not match the longer /care-plans-export route (segment boundary)");
        assertEquals("NO_ALLOW_RULE", response.errorCode());
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
                null, null, DutyContext.absent()
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
        // Authentication remains AAL1; identity proofing is independently LOA3.
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

    @Test
    @DisplayName("authentication AAL never substitutes for identity min_loa")
    void evaluate_highAal_lowIdentityLoa_deniesIdentityPolicy() {
        AuthzInternalRequest request = requestWithAssurance(3, "LOA1")
                .withAuthenticationAssurance(new AuthenticationAssurance(
                        3, List.of("webauthn"), Instant.now(), Instant.now(), true,
                        "session-aal3", "flow-aal3"));
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(buildMinLoaAllowRule(3)));

        assertEquals(Verdict.DENY, policyEngine.evaluate(request).verdict());
    }

    @Test
    @DisplayName("identity LOA never substitutes for authentication min_aal")
    void evaluate_highIdentityLoa_lowAal_deniesAuthenticationPolicy() {
        AuthzInternalRequest request = buildRequest(
                TENANT_ID, "TREATMENT", "GET:/v1/reports", "reports", null,
                DEVICE_FP, FACILITY_ID, WORKSPACE_ID, List.of("DOCTOR"), "PROVIDER", 1)
                .withAuthenticationAssurance(new AuthenticationAssurance(
                        1, List.of("pwd"), Instant.now(), null, false,
                        "session-aal1", "flow-aal1"));
        PolicyRuleEntity rule = buildAllowRuleWithConditions(
                "reports", "DOCTOR", "GET", "{\"min_aal\":2}");
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        assertEquals(Verdict.DENY, policyEngine.evaluate(request).verdict());
    }

    @Test
    @DisplayName("fresh native AAL2 satisfies method and recency policy and emits derived headers")
    void evaluate_freshNativeAal2_allowsAndEmitsAssuranceHeaders() {
        AuthzInternalRequest request = buildRequest(
                TENANT_ID, "TREATMENT", "GET:/v1/reports", "reports", null,
                DEVICE_FP, FACILITY_ID, WORKSPACE_ID, List.of("DOCTOR"), "PROVIDER", 1)
                .withAuthenticationAssurance(new AuthenticationAssurance(
                        2, List.of("pwd", "otp"), Instant.now(), Instant.now(), false,
                        "session-aal2", "flow-aal2"));
        PolicyRuleEntity rule = buildAllowRuleWithConditions(
                "reports", "DOCTOR", "GET",
                "{\"min_aal\":2,\"accepted_amr\":[\"otp\"],\"max_auth_age_seconds\":300}");
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(rule));

        AuthzResponse response = policyEngine.evaluate(request);

        assertEquals(Verdict.ALLOW, response.verdict());
        assertEquals("2", response.headerMutations().get(TrustHeaders.AUTHENTICATION_AAL));
        assertEquals("pwd,otp", response.headerMutations().get(TrustHeaders.AUTHENTICATION_AMR));
        assertEquals("session-aal2",
                response.headerMutations().get(TrustHeaders.AUTHENTICATION_SESSION_ID));
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 4.5: Delegated / act-on-behalf authorization (L5, G-CZO-03)
    // ════════════════════════════════════════════════════════════════════

    /** A request where the actor declares acting FOR a different subject (X-Subject-ID set). */
    private static AuthzInternalRequest delegatedRequest(String subjectId, int acrLoa, String assuranceLevel) {
        return new AuthzInternalRequest(
                TENANT_ID, ACTOR_ID, "CITIZEN", List.of("CITIZEN"), "TREATMENT",
                DEVICE_FP, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID,
                null, "GET", "/v1/patients/cpid-12345", "GET:/v1/patients/cpid-12345",
                "patients", "cpid-12345",
                acrLoa, "session-abc", null,
                null, null, null, null, subjectId, assuranceLevel,
                null, null, DutyContext.absent()
        );
    }

    /** A clinical request whose subject is the actor's own person; provider work context optional. */
    private static AuthzInternalRequest selfRequest(boolean providerWorkActivated) {
        return new AuthzInternalRequest(
                TENANT_ID, ACTOR_ID, "PROVIDER", List.of("CLINICIAN"), "TREATMENT",
                DEVICE_FP, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID,
                null, "GET", "/v1/patients/" + ACTOR_ID, "GET:/v1/patients/" + ACTOR_ID,
                "patients", ACTOR_ID,
                3, "session-abc", null,
                providerWorkActivated ? "PUB-PROVIDER-1" : null, null, null, null, ACTOR_ID, "LOA3",
                null, null, DutyContext.absent()
        );
    }

    @Test
    @DisplayName("Step 4.6: provider opening their OWN clinical record in work mode -> DENY (self-treatment)")
    void evaluate_selfTreatment_providerOwnRecordInWork_denies() {
        stubHappyPathDefaults(10);

        AuthzResponse response = policyEngine.evaluate(selfRequest(true));

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("SELF_TREATMENT_BLOCKED", response.errorCode());
    }

    @Test
    @DisplayName("Step 4.6: own-record access with NO active work context (My-Life) is NOT self-treatment-blocked")
    void evaluate_selfTreatment_myLifeSelfAccess_passes() {
        stubHappyPathDefaults(10);
        when(consentClient.evaluateConsent(eq(TENANT_ID), eq("patients"), eq(ACTOR_ID),
                eq(ACTOR_ID), eq("TREATMENT")))
                .thenReturn(ConsentDecision.permit("c1", List.of("read")));

        AuthzResponse response = policyEngine.evaluate(selfRequest(false));

        assertEquals(Verdict.ALLOW, response.verdict(),
                "a person reaching their own record without an active provider work context is My-Life, not self-treatment");
    }

    @Test
    @DisplayName("Step 4.5: acting for a subject with NO active delegation -> DENY")
    void evaluate_delegated_noActiveDelegation_denies() {
        stubHappyPathDefaults(10);
        when(delegationClient.resolve(eq(TENANT_ID), eq(ACTOR_ID), eq("subject-other")))
                .thenReturn(DelegationResolution.inactive());

        AuthzResponse response = policyEngine.evaluate(delegatedRequest("subject-other", 3, "LOA3"));

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("DELEGATION_NOT_ACTIVE", response.errorCode());
    }

    @Test
    @DisplayName("Step 4.5: active, in-scope, sufficiently-assured delegation + consent -> ALLOW")
    void evaluate_delegated_activeInScope_allows() {
        stubHappyPathDefaults(10);
        when(delegationClient.resolve(eq(TENANT_ID), eq(ACTOR_ID), eq("subject-other")))
                .thenReturn(new DelegationResolution(true, List.of("patients"), 2, "GUARDIAN"));
        when(consentClient.evaluateConsent(eq(TENANT_ID), eq("patients"), eq("cpid-12345"),
                eq(ACTOR_ID), eq("TREATMENT")))
                .thenReturn(ConsentDecision.permit("c1", List.of("read")));

        AuthzResponse response = policyEngine.evaluate(delegatedRequest("subject-other", 3, "LOA3"));

        assertEquals(Verdict.ALLOW, response.verdict(),
                "an active in-scope delegation with assurance met must allow (consent still applies)");
    }

    @Test
    @DisplayName("Step 4.5: delegation scope does not cover the resource -> DENY")
    void evaluate_delegated_outOfScope_denies() {
        stubHappyPathDefaults(10);
        when(delegationClient.resolve(eq(TENANT_ID), eq(ACTOR_ID), eq("subject-other")))
                .thenReturn(new DelegationResolution(true, List.of("appointments"), 2, "CAREGIVER"));

        AuthzResponse response = policyEngine.evaluate(delegatedRequest("subject-other", 3, "LOA3"));

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("DELEGATION_OUT_OF_SCOPE", response.errorCode());
    }

    @Test
    @DisplayName("Step 4.5: delegate assurance below the floor -> DENY")
    void evaluate_delegated_belowAssuranceFloor_denies() {
        stubHappyPathDefaults(10);
        when(delegationClient.resolve(eq(TENANT_ID), eq(ACTOR_ID), eq("subject-other")))
                .thenReturn(new DelegationResolution(true, List.of("patients"), 4, "GUARDIAN"));

        // effectiveLoa = max(acr=2, header LOA2) = 2 < floor 4
        AuthzResponse response = policyEngine.evaluate(delegatedRequest("subject-other", 2, "LOA2"));

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("DELEGATION_ASSURANCE_TOO_LOW", response.errorCode());
    }

    // ════════════════════════════════════════════════════════════════════
    // Step 4.7: Specially-protected confidentiality control
    //
    // The mechanism is complete; the CONTENT that decides behaviour (confidentiality ages,
    // confidential code lists) is an engineering seed pending MoHCC ratification. So the default
    // mode is SHADOW: evaluate, audit, deny nothing. These tests pin both halves — that the
    // mechanism decides correctly, and that it stays inert until the content is ratified.
    // ════════════════════════════════════════════════════════════════════

    private static final String PROTECTED_PATH = "/v1/pct/confidential/encounters/enc-1";

    /**
     * A request against the confidential clinical lane — a resource the classifier reports as
     * {@code SPECIALLY_PROTECTED}. {@code subjectId} declares whose record is being opened.
     */
    private static AuthzInternalRequest protectedRequest(String actorType, List<String> roles,
                                                         String subjectId, String purpose) {
        return new AuthzInternalRequest(
                TENANT_ID, ACTOR_ID, actorType, roles, purpose,
                DEVICE_FP, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID,
                null, "GET", PROTECTED_PATH, "GET:" + PROTECTED_PATH,
                "confidential-encounters", "enc-1",
                3, "session-abc", null,
                null, null, null, null, subjectId, "LOA3",
                null, null, DutyContext.absent()
        );
    }

    /** Risk + an unconditional ALLOW rule for the confidential lane, and permissive consent. */
    private void stubProtectedLaneHappyPath() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity allowRule = buildAllowRule("confidential-encounters", null, null, null, null);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(allowRule));
        lenient().when(consentClient.evaluateConsent(any(), anyString(), any(), anyString(), anyString()))
                .thenReturn(ConsentDecision.permit("c1", List.of("read")));
    }

    /** A rule whose visibility overlay asks for specific confidential categories. */
    private void stubProtectedLaneWithRuleCategories(String categoriesJson) {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        lenient().when(consentClient.evaluateConsent(any(), anyString(), any(), anyString(), anyString()))
                .thenReturn(ConsentDecision.permit("c1", List.of("read")));
        PolicyRuleEntity allowRule = buildAllowRule("confidential-encounters", null, null, null, null);
        allowRule.setConditions("{\"visibility\":{\"confidentialCategories\":" + categoriesJson + "}}");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(allowRule));
    }

    /** Make the governed pack ratified and effective, granting the given categories. */
    private void ratifyPack(String... categories) {
        when(confidentialityPack.isEffective()).thenReturn(true);
        lenient().when(confidentialityPack.retainGrantable(anyList()))
                .thenAnswer(inv -> {
                    List<String> requested = inv.getArgument(0);
                    Set<String> permitted = new LinkedHashSet<>();
                    for (String r : requested) {
                        for (String c : categories) {
                            if (c.equalsIgnoreCase(r)) {
                                permitted.add(c);
                            }
                        }
                    }
                    return permitted;
                });
    }

    private static List<String> grantedCategories(AuthzResponse response) {
        return response.obligations().visibilityProfile().confidentialCategories();
    }

    private static String capturedGovernanceEvent(AuditPublisher publisher) {
        ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
        verify(publisher).queueGovernanceEvent(eventType.capture(), anyString(), anyMap());
        return eventType.getValue();
    }

    // ── Inertness: the shipped default must change nothing ───────────────────

    @Test
    @DisplayName("Step 4.7 SHADOW (default): a guardian is NOT denied, but the refusal IS audited")
    void evaluate_protected_shadowMode_auditsWithoutDenying() {
        stubProtectedLaneHappyPath();
        when(delegationClient.resolve(eq(TENANT_ID), eq(ACTOR_ID), eq("child-cpid-1")))
                .thenReturn(new DelegationResolution(true, List.of("*"), 2, "GUARDIAN"));

        AuthzResponse response = policyEngine.evaluate(
                protectedRequest("CITIZEN", List.of("CITIZEN"), "child-cpid-1", "TREATMENT"));

        assertEquals(Verdict.ALLOW, response.verdict(),
                "the mechanism ships inert — it must not change behaviour before the content is ratified");
        assertEquals("CONFIDENTIAL_ACCESS_REFUSED", capturedGovernanceEvent(auditPublisher),
                "inert must still mean observable: the audit records what it WOULD have refused");
        assertTrue(grantedCategories(response) == null || grantedCategories(response).isEmpty(),
                "shadow grants nothing either — no record may carry a label that does not protect it");
    }

    @Test
    @DisplayName("Step 4.7 OFF: no evaluation, no audit, no grant")
    void evaluate_protected_offMode_doesNothing() {
        properties.setConfidentialityMode("OFF");
        stubProtectedLaneHappyPath();

        AuthzResponse response = policyEngine.evaluate(
                protectedRequest("CITIZEN", List.of("CITIZEN"), ACTOR_ID, "TREATMENT"));

        assertEquals(Verdict.ALLOW, response.verdict());
        verify(auditPublisher, never()).queueGovernanceEvent(anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("Step 4.7 ENFORCE with an unratified pack stays inert and says so LOUDLY")
    void evaluate_protected_enforceWithInertPack_staysInertButAudits() {
        properties.setConfidentialityMode("ENFORCE");
        when(confidentialityPack.isEffective()).thenReturn(false);
        stubProtectedLaneHappyPath();
        when(delegationClient.resolve(eq(TENANT_ID), eq(ACTOR_ID), eq("child-cpid-1")))
                .thenReturn(new DelegationResolution(true, List.of("*"), 2, "GUARDIAN"));

        AuthzResponse response = policyEngine.evaluate(
                protectedRequest("CITIZEN", List.of("CITIZEN"), "child-cpid-1", "TREATMENT"));

        assertEquals(Verdict.ALLOW, response.verdict(),
                "enforcing on an engineering seed could hide records from the clinicians treating "
                        + "the person, so it refuses to enforce");
        ArgumentCaptor<String> events = ArgumentCaptor.forClass(String.class);
        verify(auditPublisher, atLeastOnce()).queueGovernanceEvent(events.capture(), anyString(), anyMap());
        assertTrue(events.getAllValues().contains("CONFIDENTIALITY_ENFORCE_UNAVAILABLE"),
                "an operator must never believe a control is live when it is not — that belief is "
                        + "the false assurance this seam exists to eliminate");
    }

    // ── The mechanism, proven under ENFORCE with a ratified pack ─────────────

    @Test
    @DisplayName("Step 4.7 ENFORCE: a guardian with a fully valid delegation cannot read a protected record")
    void evaluate_protected_guardianContext_denies() {
        properties.setConfidentialityMode("ENFORCE");
        ratifyPack("SAFEGUARDING");
        stubProtectedLaneHappyPath();
        // A delegation that is active, unexpired, wildcard-scoped and above the assurance floor —
        // every Step 4.5 gate passes. Only the confidentiality control can stop this.
        when(delegationClient.resolve(eq(TENANT_ID), eq(ACTOR_ID), eq("child-cpid-1")))
                .thenReturn(new DelegationResolution(true, List.of("*"), 2, "GUARDIAN"));

        AuthzResponse response = policyEngine.evaluate(
                protectedRequest("CITIZEN", List.of("CITIZEN"), "child-cpid-1", "TREATMENT"));

        assertEquals(Verdict.DENY, response.verdict(),
                "guardian/caregiver context must never reach a specially-protected record");
        assertEquals("PROTECTED_RECORD_DELEGATE_DENIED", response.errorCode());
    }

    @Test
    @DisplayName("Step 4.7 ENFORCE: a CAREGIVER delegation is refused just like a guardian")
    void evaluate_protected_caregiverContext_denies() {
        properties.setConfidentialityMode("ENFORCE");
        ratifyPack("SAFEGUARDING");
        stubProtectedLaneHappyPath();
        when(delegationClient.resolve(eq(TENANT_ID), eq(ACTOR_ID), eq("child-cpid-1")))
                .thenReturn(new DelegationResolution(true, List.of("*"), 2, "CAREGIVER"));

        AuthzResponse response = policyEngine.evaluate(
                protectedRequest("CITIZEN", List.of("CITIZEN"), "child-cpid-1", "TREATMENT"));

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("PROTECTED_RECORD_DELEGATE_DENIED", response.errorCode());
    }

    @Test
    @DisplayName("Step 4.7 ENFORCE: a rule grant does NOT rescue a delegated request")
    void evaluate_protected_guardianWithEntitledRule_stillDenies() {
        properties.setConfidentialityMode("ENFORCE");
        ratifyPack("SAFEGUARDING");
        stubProtectedLaneWithRuleCategories("[\"SAFEGUARDING\"]");
        when(delegationClient.resolve(eq(TENANT_ID), eq(ACTOR_ID), eq("child-cpid-1")))
                .thenReturn(new DelegationResolution(true, List.of("*"), 2, "GUARDIAN"));

        AuthzResponse response = policyEngine.evaluate(
                protectedRequest("CITIZEN", List.of("CITIZEN"), "child-cpid-1", "TREATMENT"));

        assertEquals(Verdict.DENY, response.verdict(),
                "delegate exclusion is absolute — the governance channel must not reopen the hole");
        assertEquals("PROTECTED_RECORD_DELEGATE_DENIED", response.errorCode());
    }

    @Test
    @DisplayName("Step 4.7: the person themselves is granted every category of their own record")
    void evaluate_protected_selfAccess_grantsAllCategories() {
        properties.setConfidentialityMode("ENFORCE");
        ratifyPack("HIV");
        stubProtectedLaneHappyPath();

        AuthzResponse response = policyEngine.evaluate(
                protectedRequest("CITIZEN", List.of("CITIZEN"), ACTOR_ID, "TREATMENT"));

        assertEquals(Verdict.ALLOW, response.verdict(),
                "the adolescent whose record it is must be able to read it");
        assertEquals(List.of("*"), grantedCategories(response));
    }

    @Test
    @DisplayName("Step 4.7 ENFORCE: a clinician with no governed category grant is refused")
    void evaluate_protected_clinicianWithoutEntitlement_denies() {
        properties.setConfidentialityMode("ENFORCE");
        ratifyPack("SAFEGUARDING");
        stubProtectedLaneHappyPath();

        // subjectId null: a clinician acts in their OWN professional capacity. Setting X-Subject-ID
        // to someone else is the act-on-behalf marker, handled by Step 4.5.
        AuthzResponse response = policyEngine.evaluate(
                protectedRequest("PROVIDER", List.of("DOCTOR"), null, "TREATMENT"));

        assertEquals(Verdict.DENY, response.verdict(),
                "reaching protected content needs an explicit governed grant, not a clinical role");
        assertEquals("PROTECTED_RECORD_NOT_ENTITLED", response.errorCode());
    }

    @Test
    @DisplayName("Step 4.7 ENFORCE: a governed rule grant entitles the clinician to THAT category only")
    void evaluate_protected_ruleGrant_isCategoryScoped() {
        properties.setConfidentialityMode("ENFORCE");
        ratifyPack("SAFEGUARDING", "MENTAL_HEALTH");
        stubProtectedLaneWithRuleCategories("[\"SAFEGUARDING\"]");

        AuthzResponse response = policyEngine.evaluate(
                protectedRequest("PROVIDER", List.of("DOCTOR"), null, "TREATMENT"));

        assertEquals(Verdict.ALLOW, response.verdict(),
                "the governed policy rule is the seam that grants protected access");
        assertEquals(List.of("SAFEGUARDING"), grantedCategories(response),
                "a safeguarding grant must not sweep in the adolescent's mental-health notes — the "
                        + "case an ordered visibility tier could not express");
    }

    @Test
    @DisplayName("Step 4.7 ENFORCE: a category the ratified pack does not permit is dropped, not granted")
    void evaluate_protected_categoryOutsidePack_isDropped() {
        properties.setConfidentialityMode("ENFORCE");
        ratifyPack("SAFEGUARDING");
        stubProtectedLaneWithRuleCategories("[\"HIV\"]");

        AuthzResponse response = policyEngine.evaluate(
                protectedRequest("PROVIDER", List.of("DOCTOR"), null, "TREATMENT"));

        assertEquals(Verdict.DENY, response.verdict());
        assertEquals("PROTECTED_RECORD_PACK_INERT", response.errorCode(),
                "a rule asking for a category the ratified pack does not carry must fail visibly, "
                        + "not quietly grant something else");
    }

    @Test
    @DisplayName("Step 4.7: a legacy visibilityTier-only overlay grants no confidential category")
    void evaluate_protected_legacyTierOverlay_grantsNothing() {
        properties.setConfidentialityMode("ENFORCE");
        ratifyPack("SAFEGUARDING");
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        lenient().when(consentClient.evaluateConsent(any(), anyString(), any(), anyString(), anyString()))
                .thenReturn(ConsentDecision.permit("c1", List.of("read")));
        PolicyRuleEntity allowRule = buildAllowRule("confidential-encounters", null, null, null, null);
        allowRule.setConditions("{\"visibility\":{\"visibilityTier\":\"FULL_IDENTIFIED_CLINICAL\"}}");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(allowRule));

        AuthzResponse response = policyEngine.evaluate(
                protectedRequest("PROVIDER", List.of("DOCTOR"), null, "TREATMENT"));

        assertEquals(Verdict.DENY, response.verdict(),
                "confidentiality is a category obligation, not a disclosure tier");
        assertEquals("PROTECTED_RECORD_NOT_ENTITLED", response.errorCode());
    }

    // ── Emergency access is a hard requirement ───────────────────────────────

    @Test
    @DisplayName("Step 4.7: EMERGENCY purpose waives confidentiality (over-restricting kills people too)")
    void evaluate_protected_emergencyPurpose_waives() {
        properties.setConfidentialityMode("ENFORCE");
        ratifyPack("HIV");
        stubProtectedLaneHappyPath();

        AuthzResponse response = policyEngine.evaluate(
                protectedRequest("PROVIDER", List.of("DOCTOR"), null, "EMERGENCY"));

        assertEquals(Verdict.ALLOW, response.verdict(),
                "a teenager arriving unconscious must not have the HIV status that explains the "
                        + "presentation hidden from the clinician treating them");
        assertEquals(List.of("*"), grantedCategories(response));
    }

    @Test
    @DisplayName("Step 4.7: the emergency waiver reaches protected content even for a delegate")
    void evaluate_protected_emergencyWaiver_precedesDelegateExclusion() {
        properties.setConfidentialityMode("ENFORCE");
        ratifyPack("HIV");
        stubProtectedLaneHappyPath();
        lenient().when(delegationClient.resolve(eq(TENANT_ID), eq(ACTOR_ID), eq("child-cpid-1")))
                .thenReturn(new DelegationResolution(true, List.of("*"), 2, "GUARDIAN"));

        AuthzResponse response = policyEngine.evaluate(
                protectedRequest("PROVIDER", List.of("DOCTOR"), "child-cpid-1", "EMERGENCY"));

        assertEquals(Verdict.ALLOW, response.verdict(),
                "an accompanying adult handing over a collapsed teenager must not be the reason "
                        + "the clinical picture stays hidden — audited, not blocked");
    }

    @Test
    @DisplayName("Step 4.7: every emergency waiver is audited as a grant")
    void evaluate_protected_emergencyWaiver_isAudited() {
        properties.setConfidentialityMode("ENFORCE");
        ratifyPack("HIV");
        stubProtectedLaneHappyPath();

        policyEngine.evaluate(protectedRequest("PROVIDER", List.of("DOCTOR"), null, "EMERGENCY"));

        assertEquals("CONFIDENTIAL_ACCESS_GRANTED", capturedGovernanceEvent(auditPublisher),
                "the waiver is detection, not prevention — it is only safe if it is reviewable");
    }

    // ── Ordinary care is untouched ──────────────────────────────────────────

    @Test
    @DisplayName("Step 4.7: a guardian delegation still reads ORDINARY clinical data (no over-denial)")
    void evaluate_ordinaryClinical_guardianContext_stillAllows() {
        properties.setConfidentialityMode("ENFORCE");
        stubHappyPathDefaults(10);
        when(delegationClient.resolve(eq(TENANT_ID), eq(ACTOR_ID), eq("subject-other")))
                .thenReturn(new DelegationResolution(true, List.of("patients"), 2, "GUARDIAN"));
        when(consentClient.evaluateConsent(eq(TENANT_ID), eq("patients"), eq("cpid-12345"),
                eq(ACTOR_ID), eq("TREATMENT")))
                .thenReturn(ConsentDecision.permit("c1", List.of("read")));

        AuthzResponse response = policyEngine.evaluate(delegatedRequest("subject-other", 3, "LOA3"));

        assertEquals(Verdict.ALLOW, response.verdict(),
                "the confidentiality control must narrow protected data only, not ordinary care");
        verify(auditPublisher, never()).queueGovernanceEvent(anyString(), anyString(), anyMap());
    }


    // ════════════════════════════════════════════════════════════════════
    // Phase 3: OPA shadow (strangler — must never change the verdict)
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("OPA SHADOW: a divergent OPA verdict is logged but never changes the Java decision")
    void evaluate_opaShadow_doesNotAffectVerdict() {
        properties.setOpaMode("SHADOW");
        stubHappyPathDefaults(10);
        when(consentClient.evaluateConsent(eq(TENANT_ID), eq("patients"), eq("cpid-12345"),
                eq(ACTOR_ID), eq("TREATMENT")))
                .thenReturn(ConsentDecision.permit("c", List.of("read")));
        when(opaDecisionClient.decide(any()))
                .thenReturn(new OpaDecision(false, List.of("MIN_LOA"))); // OPA would DENY

        AuthzResponse response = policyEngine.evaluate(requestWithResourceId("patients", "cpid-12345"));

        assertEquals(Verdict.ALLOW, response.verdict(), "SHADOW OPA divergence must not change the verdict");
        verify(opaDecisionClient).decide(any());
    }

    @Test
    @DisplayName("OPA OFF (default): the OPA sidecar is never called")
    void evaluate_opaOff_neverCallsOpa() {
        stubHappyPathDefaults(10);
        when(consentClient.evaluateConsent(eq(TENANT_ID), eq("patients"), eq("cpid-12345"),
                eq(ACTOR_ID), eq("TREATMENT")))
                .thenReturn(ConsentDecision.permit("c", List.of("read")));

        policyEngine.evaluate(requestWithResourceId("patients", "cpid-12345"));

        verifyNoInteractions(opaDecisionClient);
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
        AuthzInternalRequest request = withFreshAal2(
                requestWithAction("DELETE:/v1/patients/abc", "patients"));
        stubHappyPathDefaults(65);

        when(consentClient.evaluateConsent(
                eq(TENANT_ID), eq("patients"), isNull(),
                eq(ACTOR_ID), eq("TREATMENT")))
                .thenReturn(ConsentDecision.permit("consent-step-up", List.of("delete")));

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

    // ════════════════════════════════════════════════════════════════════
    // WS#5 GAP-1: Assets / Equipment policy rules (V026) at the gateway PDP.
    // Mirrors the V026 seed shape — wildcard resource_type pinned by path_contains
    // to /internal/v1/equipment (or /assets), min_loa>=2 on writes, DENY-wins for
    // citizen / stock-as-asset / retire-by-cadre. The DB orders effect DESC so the
    // DENY rules are presented to the engine first (as the live cache does).
    // ════════════════════════════════════════════════════════════════════

    /** An asset-domain request: actor type STAFF, given roles, on the supplied equipment/asset path. */
    private static AuthzInternalRequest assetRequest(String path, String httpMethod,
                                                     String action, List<String> roles,
                                                     String actorType, String assuranceLevel) {
        // resourceType = last path segment (what the gateway derives + caches on)
        String norm = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        String resourceType = norm.substring(norm.lastIndexOf('/') + 1);
        return new AuthzInternalRequest(
                TENANT_ID, ACTOR_ID, actorType, roles, "OPERATIONS",
                DEVICE_FP, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID,
                null, httpMethod, path, action, resourceType, null,
                3, "session-abc", null,
                null, null, null, null, null, assuranceLevel,
                null, null, DutyContext.absent()
        );
    }

    /** Build the V026 ALLOW rule for an asset cadre: wildcard pinned to the equipment path + min_loa. */
    private PolicyRuleEntity assetOpsAllowRule(String role) {
        PolicyRuleEntity rule = buildAllowRule(null, null, role, null, null);
        rule.setName("asset-ops-" + role.toLowerCase());
        rule.setConditions("{\"path_contains\": \"/internal/v1/equipment\", \"min_loa\": 2}");
        rule.setPriority(60);
        return rule;
    }

    /** Build the V026 citizen DENY rule (pinned to the equipment path). */
    private PolicyRuleEntity assetCitizenDenyRule() {
        PolicyRuleEntity rule = buildAllowRule(null, null, "CITIZEN", null, null);
        rule.setName("asset-deny-citizen-equipment");
        rule.setEffect("DENY");
        rule.setPriority(10);
        rule.setConditions("{\"path_contains\": \"/internal/v1/equipment\"}");
        return rule;
    }

    @Test
    @DisplayName("WS#5 V026: FACILITY_ADMIN registering equipment (LOA2) -> ALLOW")
    void evaluate_assetFacilityAdminRegister_allows() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(assetOpsAllowRule("FACILITY_ADMIN")));

        AuthzInternalRequest req = assetRequest(
                "/internal/v1/equipment", "POST", "POST:/internal/v1/equipment",
                List.of("FACILITY_ADMIN"), "STAFF", "LOA2");

        assertEquals(Verdict.ALLOW, policyEngine.evaluate(req).verdict(),
                "FACILITY_ADMIN at LOA2 registering equipment must be ALLOWed by the V026 rule");
    }

    @Test
    @DisplayName("WS#5 V026: ASSET_TECHNICIAN updating an assigned maintenance task (LOA2) -> ALLOW")
    void evaluate_assetTechnicianMaintenanceUpdate_allows() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(assetOpsAllowRule("ASSET_TECHNICIAN")));

        AuthzInternalRequest req = assetRequest(
                "/internal/v1/equipment/maintenance/task-1", "POST",
                "POST:/internal/v1/equipment/maintenance/task-1",
                List.of("ASSET_TECHNICIAN"), "STAFF", "LOA2");

        assertEquals(Verdict.ALLOW, policyEngine.evaluate(req).verdict(),
                "ASSET_TECHNICIAN at LOA2 on the maintenance path must be ALLOWed (by-assignment enforced in-service)");
    }

    @Test
    @DisplayName("WS#5 V026: CITIZEN reaching the equipment domain -> DENY (DENY-wins over any ALLOW)")
    void evaluate_assetCitizen_denies() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        // DB orders DENY first (effect DESC); the citizen also carries no operational ALLOW.
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(assetCitizenDenyRule(), assetOpsAllowRule("FACILITY_ADMIN")));

        AuthzInternalRequest req = assetRequest(
                "/internal/v1/equipment", "GET", "GET:/internal/v1/equipment",
                List.of("CITIZEN"), "CITIZEN", "LOA2");

        AuthzResponse resp = policyEngine.evaluate(req);
        assertEquals(Verdict.DENY, resp.verdict(), "CITIZEN must be DENIED the operational asset domain");
        assertEquals("POLICY_DENY", resp.errorCode());
    }

    @Test
    @DisplayName("WS#5 V026: stock action posed as an asset function -> DENY (Dura owns stock)")
    void evaluate_assetStockAsAsset_denies() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity stockDeny = buildAllowRule(null, null, null, null, null);
        stockDeny.setName("asset-deny-stock-as-asset");
        stockDeny.setEffect("DENY");
        stockDeny.setPriority(10);
        stockDeny.setConditions("{\"path_contains\": [\"/internal/v1/equipment/stock\", \"/stock/adjust\"]}");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(stockDeny, assetOpsAllowRule("FACILITY_ADMIN")));

        AuthzInternalRequest req = assetRequest(
                "/internal/v1/equipment/stock/adjust", "POST",
                "POST:/internal/v1/equipment/stock/adjust",
                List.of("FACILITY_ADMIN"), "STAFF", "LOA2");

        AuthzResponse resp = policyEngine.evaluate(req);
        assertEquals(Verdict.DENY, resp.verdict(), "A stock action on the asset path must be DENIED");
        assertEquals("POLICY_DENY", resp.errorCode());
    }

    @Test
    @DisplayName("WS#5 V026: ASSET_TECHNICIAN retiring equipment -> DENY (elevated approval / admin only)")
    void evaluate_assetTechnicianRetire_denies() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        PolicyRuleEntity retireDeny = buildAllowRule(null, null, "ASSET_TECHNICIAN", "POST", null);
        retireDeny.setName("asset-deny-retire-technician");
        retireDeny.setEffect("DENY");
        retireDeny.setPriority(9);
        retireDeny.setConditions("{\"path_contains\": [\"/internal/v1/equipment/retire\", \"/internal/v1/equipment/dispose\"]}");
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(retireDeny, assetOpsAllowRule("ASSET_TECHNICIAN")));

        AuthzInternalRequest req = assetRequest(
                "/internal/v1/equipment/retire", "POST",
                "POST:/internal/v1/equipment/retire",
                List.of("ASSET_TECHNICIAN"), "STAFF", "LOA2");

        AuthzResponse resp = policyEngine.evaluate(req);
        assertEquals(Verdict.DENY, resp.verdict(), "Technician retire must be DENIED (elevated approval required)");
        assertEquals("POLICY_DENY", resp.errorCode());
    }

    @Test
    @DisplayName("WS#5 V026: a low-trust write (LOA1) below min_loa=2 -> DENY (no matching ALLOW)")
    void evaluate_assetLowTrustWrite_denies() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(assetOpsAllowRule("FACILITY_ADMIN")));

        // ACR loaLevel is 3 in assetRequest; force effective LOA to 1 by passing acr via a low-LOA request.
        AuthzInternalRequest base = assetRequest(
                "/internal/v1/equipment", "POST", "POST:/internal/v1/equipment",
                List.of("FACILITY_ADMIN"), "STAFF", "LOA1");
        AuthzInternalRequest req = new AuthzInternalRequest(
                TENANT_ID, ACTOR_ID, "STAFF", List.of("FACILITY_ADMIN"), "OPERATIONS",
                DEVICE_FP, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID,
                null, "POST", "/internal/v1/equipment", "POST:/internal/v1/equipment",
                base.resourceType(), null,
                1, "session-abc", null,
                null, null, null, null, null, "LOA1",
                null, null, DutyContext.absent());

        AuthzResponse resp = policyEngine.evaluate(req);
        assertEquals(Verdict.DENY, resp.verdict(),
                "A write below min_loa=2 must not match the conditioned ALLOW -> DENY");
        assertEquals("NO_ALLOW_RULE", resp.errorCode());
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
