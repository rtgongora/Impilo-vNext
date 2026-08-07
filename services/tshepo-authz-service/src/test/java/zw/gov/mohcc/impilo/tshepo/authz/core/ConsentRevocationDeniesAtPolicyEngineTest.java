package zw.gov.mohcc.impilo.tshepo.authz.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.PolicyDecisionLogRepository;
import zw.gov.mohcc.impilo.tshepo.authz.service.*;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

/**
 * Phase 0 gate condition #3, PDP half: a revoked consent must turn into a DENY at the decision
 * point, and an unreachable consent service must never yield access.
 *
 * <h2>What is real here, and what is not</h2>
 *
 * <p>The {@link PolicyEngine} and the {@link ConsentClient} are both real, and they are joined by a
 * real {@link RestTemplate} speaking real HTTP. Nothing stubs {@code ConsentDecision}: the engine
 * receives whatever the client parses out of the wire bytes, so the envelope unwrapping, the
 * reason-code mapping and the fail-closed catch-all are all exercised rather than asserted.</p>
 *
 * <p>What is canned is the consent service's <em>response body</em> — and only because the producer
 * lives in another Maven module. The two response shapes below are not invented: they are the exact
 * decisions {@code ConsentRevocationBlocksReadTest} (tshepo-consent-service) proves the real
 * evaluator returns against a real database before and after
 * {@code ConsentCrudService.revoke} — a PERMIT carrying the directive id, then
 * {@code NO_ACTIVE_CONSENT}. {@code ConsentClientContractTest} independently pins that this client
 * asks that producer the right question. Those three classes together are the chain; this one is
 * its last link.</p>
 *
 * <h2>Reaching the consent step at all</h2>
 *
 * <p>Consent is Step 5. Step 4 returns {@code NO_ALLOW_RULE} early when no rule matches, so a
 * clinical request that has no ALLOW rule never reaches consent evaluation. That is not
 * hypothetical: on 2026-08-07 every clinical decision in the live preview estate
 * ({@code tshepo_authz.policy_decision_log}) was refused at Step 4, so consent has never once been
 * evaluated in the estate. These tests therefore seed an ALLOW rule deliberately — without it they
 * would pass for the wrong reason, showing a DENY that consent had no part in.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 0 D1: revoked consent denies at the PDP, and an unreachable consent service fails closed")
class ConsentRevocationDeniesAtPolicyEngineTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock private DeviceRiskScoreEvaluator riskScoring;
    @Mock private PolicyCacheService policyCacheService;
    @Mock private ProviderPrivilegeRevocationStore privilegeRevocationStore;
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

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CORRELATION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID FACILITY_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final UUID WORKSPACE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String ACTOR_ID = "actor-123";
    private static final String DEVICE_FP = "fp-sha256-abcdef";
    private static final String PATIENT_CPID = "cpid-12345";

    /** The decision the real evaluator returns for an active, in-period, scope-matching grant. */
    private static final String PERMIT_BODY =
            "{\"data\":{\"permitted\":true,\"consentId\":\"11111111-1111-1111-1111-111111111111\","
                    + "\"provision\":\"permit\",\"allowedScopes\":[\"clinical-data\"]},"
                    + "\"correlationId\":\"c-1\"}";

    /** The decision the real evaluator returns once that grant has been revoked. */
    private static final String REVOKED_BODY =
            "{\"data\":{\"permitted\":false,\"reason\":\"NO_ACTIVE_CONSENT\"},\"correlationId\":\"c-1\"}";

    private RestTemplate restTemplate;
    private MockRestServiceServer consentService;
    private PolicyEngine policyEngine;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        consentService = MockRestServiceServer.createServer(restTemplate);
        policyEngine = buildEngine(new ConsentClient(restTemplate, consentProperties()));
    }

    // ════════════════════════════════════════════════════════════════════
    // The chained proof, at the decision point
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the same clinical read is ALLOWed while consent stands and DENIED once it is revoked")
    void revokedConsent_flipsTheDecisionToDeny() {
        stubHappyPathDefaults();

        // Phase 1 — consent stands.
        consentService.expect(requestTo(containsString("/v1/consent/evaluate")))
                .andRespond(withSuccess(PERMIT_BODY, MediaType.APPLICATION_JSON));

        AuthzResponse whileGranted = policyEngine.evaluate(clinicalRead());

        assertThat(whileGranted.verdict())
                .as("a clinical read with a matching ALLOW rule and standing consent must be allowed")
                .isEqualTo(Verdict.ALLOW);
        consentService.verify();

        // Phase 2 — the person revokes. Nothing about the request changes.
        consentService.reset();
        consentService.expect(requestTo(containsString("/v1/consent/evaluate")))
                .andRespond(withSuccess(REVOKED_BODY, MediaType.APPLICATION_JSON));

        AuthzResponse afterRevocation = policyEngine.evaluate(clinicalRead());

        assertThat(afterRevocation.verdict())
                .as("THE GATE: the identical read must be refused once consent is revoked")
                .isEqualTo(Verdict.DENY);
        assertThat(afterRevocation.errorCode())
                .as("a withdrawn grant leaves no directive behind, so the honest code is the "
                        + "obtainable one — the person can be asked again")
                .isEqualTo("CONSENT_REQUIRED");
        consentService.verify();
    }

    // ════════════════════════════════════════════════════════════════════
    // Fail-closed — the property that actually protects patients
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("fail-closed: a consent service that cannot be reached at all DENIES, never ALLOWs")
    void consentServiceUnreachable_denies() {
        stubHappyPathDefaults();

        // A genuinely dead endpoint, not a stubbed exception: port 1 has no listener, so the real
        // client takes a real IOException through its real catch-all. A mocked ConsentClient cannot
        // show this, which is exactly how a fail-open regression would get through review.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(250);
        factory.setReadTimeout(250);
        AuthzProperties unreachable = consentProperties();
        unreachable.setConsentServiceUrl("http://127.0.0.1:1");
        PolicyEngine engine = buildEngine(new ConsentClient(new RestTemplate(factory), unreachable));

        AuthzResponse response = engine.evaluate(clinicalRead());

        assertThat(response.verdict())
                .as("an unreachable consent service must never yield access")
                .isEqualTo(Verdict.DENY);
        assertThat(response.errorCode())
                .as("and the refusal must not masquerade as the person having withheld consent")
                .isEqualTo("CONSENT_DENIED");
    }

    @Test
    @DisplayName("fail-closed: a consent service returning 500 DENIES")
    void consentServiceErroring_denies() {
        stubHappyPathDefaults();
        consentService.expect(requestTo(containsString("/v1/consent/evaluate")))
                .andRespond(withServerError());

        assertThat(policyEngine.evaluate(clinicalRead()).verdict())
                .as("a broken consent service must not become an open door")
                .isEqualTo(Verdict.DENY);
    }

    @Test
    @DisplayName("fail-closed: an envelope carrying an error rather than a decision DENIES")
    void consentServiceReturningErrorEnvelope_denies() {
        stubHappyPathDefaults();
        consentService.expect(requestTo(containsString("/v1/consent/evaluate")))
                .andRespond(withSuccess("{\"error\":{\"code\":\"INTERNAL\"}}", MediaType.APPLICATION_JSON));

        assertThat(policyEngine.evaluate(clinicalRead()).verdict())
                .as("a 200 that carries no decision must not be read as permission")
                .isEqualTo(Verdict.DENY);
    }

    // ════════════════════════════════════════════════════════════════════
    // Negative control — consent is genuinely what decided the DENY above
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("negative control: a non-clinical resource never consults consent at all")
    void nonClinicalResource_doesNotConsultConsent() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(allowRuleFor("appointments")));

        // No consent response is queued. MockRestServiceServer fails the test on any unexpected
        // call, so this asserts the absence of the call rather than trusting it.
        AuthzResponse response = policyEngine.evaluate(
                request("appointments", "appt-1"));

        assertThat(response.verdict()).isEqualTo(Verdict.ALLOW);
        consentService.verify();
    }

    // ════════════════════════════════════════════════════════════════════
    // Fixtures
    // ════════════════════════════════════════════════════════════════════

    private PolicyEngine buildEngine(ConsentClient consentClient) {
        lenient().when(privilegeRevocationStore.isRevoked(anyString())).thenReturn(false);
        lenient().when(visibilityEscalationService.resolveActiveGrant(any())).thenReturn(Optional.empty());
        lenient().when(confidentialityPack.isEffective()).thenReturn(false);
        lenient().when(confidentialityPack.retainGrantable(anyList())).thenReturn(java.util.Set.of());
        lenient().when(confidentialityPack.stateLabel()).thenReturn("INERT(ENGINEERING_SEED,effective=false)");
        lenient().when(confidentialityPack.packId()).thenReturn("impilo.confidentiality.adolescent");
        lenient().when(confidentialityPack.version()).thenReturn("engineering-seed-1.0.0");
        return new PolicyEngine(
                riskScoring, policyCacheService, privilegeRevocationStore, consentClient,
                stepUpService, breakGlassService, decisionLogRepository,
                auditPublisher, defaultProperties(), new ObjectMapper(), visibilityEscalationService,
                delegationClient, opaDecisionClient, roleTemplateCatalog, confidentialityPack,
                decisionEnvelopeSigner, meterRegistry);
    }

    /** Risk low enough not to trigger step-up, and an ALLOW rule so Step 4 does not short-circuit. */
    private void stubHappyPathDefaults() {
        when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);
        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(allowRuleFor("patients")));
    }

    private static AuthzInternalRequest clinicalRead() {
        return request("patients", PATIENT_CPID);
    }

    private static AuthzInternalRequest request(String resourceType, String resourceId) {
        return new AuthzInternalRequest(
                TENANT_ID, ACTOR_ID, "PROVIDER", List.of("DOCTOR"), "TREATMENT",
                DEVICE_FP, CORRELATION_ID, FACILITY_ID, WORKSPACE_ID,
                null, "GET", "/v1/" + resourceType, "GET:/v1/" + resourceType + "/" + resourceId,
                resourceType, resourceId,
                3, "session-abc", null,
                null, null, null, null, null, null,
                null, null, DutyContext.absent());
    }

    private PolicyRuleEntity allowRuleFor(String resourceType) {
        PolicyRuleEntity rule = new PolicyRuleEntity();
        rule.setTenantId(TENANT_ID);
        rule.setName("d1-consent-proof-allow-rule");
        rule.setResourceType(resourceType);
        rule.setEffect("ALLOW");
        rule.setFacilityScope(false);
        rule.setWorkspaceScope(false);
        rule.setActive(true);
        rule.setPriority(100);
        return rule;
    }

    private static AuthzProperties consentProperties() {
        AuthzProperties props = defaultProperties();
        props.setConsentServiceUrl("http://tshepo-consent-service:8182");
        props.setConsentEvaluatePath("/v1/consent/evaluate");
        return props;
    }

    private static AuthzProperties defaultProperties() {
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
        props.setConsentServiceUrl("http://tshepo-consent-service:8182");
        props.setConsentEvaluatePath("/v1/consent/evaluate");
        return props;
    }
}
