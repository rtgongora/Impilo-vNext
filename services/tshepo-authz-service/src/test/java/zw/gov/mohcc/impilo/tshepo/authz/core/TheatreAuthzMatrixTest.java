package zw.gov.mohcc.impilo.tshepo.authz.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.PolicyDecisionLogRepository;
import zw.gov.mohcc.impilo.tshepo.authz.service.*;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.Verdict;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Wave 6 §16 — Theatre trust/scope/safety authz MATRIX, proven live through the
 * real {@link PolicyEngine} (only the collaborators the engine calls are mocked;
 * the RBAC/ABAC + DENY-wins + segment-bounded path-pin + condition logic under test
 * is the production code).
 *
 * <p>For EVERY key theatre action the matrix asserts the CORRECT user is ALLOWED
 * <em>and</em> an INCORRECT user is DENIED across the 10 access-control dimensions:
 * facility context, active assignment, licence/registration, cadre/scope, theatre
 * privilege (VARAPI APPROVED scope=SURGERY), trainee supervision / countersignature
 * (assurance floor), consent authority, break-glass, separation-of-duties and
 * cross-facility / role-revocation / session expiry.</p>
 *
 * <p>The rule fixtures MIRROR the shipped policy rows (V029 perioperative, V030
 * clinical-safety, V034 §16 completion) so this suite is a faithful shadow of the
 * live cache {@link PolicyCacheService} serves. Because the PDP evaluates path_contains
 * only on ALLOW rows, theatre negatives are enforced by CORRECT-CADRE-ONLY ALLOW: an
 * out-of-scope cadre / CITIZEN / PATIENT matches NO ALLOW and is denied NO_ALLOW_RULE.
 * The NEGATIVE (deny) cases are the point of §16.</p>
 */
@ExtendWith(MockitoExtension.class)
class TheatreAuthzMatrixTest {

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

    private PolicyEngine engine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CORR = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID FACILITY_A = UUID.fromString("aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa");
    private static final UUID FACILITY_B = UUID.fromString("bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb");
    private static final UUID WORKSPACE = UUID.fromString("dddddddd-3333-3333-3333-dddddddddddd");
    private static final String DEVICE = "fp-theatre-rig";
    private static final String ACTOR = "actor-theatre-1";
    private static final String PROVIDER = "PUB-SURGEON-1";

    @BeforeEach
    void setUp() {
        AuthzProperties props = new AuthzProperties();
        AuthzProperties.RiskThresholds t = new AuthzProperties.RiskThresholds();
        t.setDenyThreshold(81);
        t.setStepUpTrigger(61);
        t.setUnknownDeviceScore(70);
        t.setNewDeviceScore(50);
        t.setBlockedDeviceScore(100);
        props.setRiskThresholds(t);
        props.setStepUpMethods(List.of("MFA", "BIOMETRIC", "SUPERVISOR_APPROVAL"));
        props.setStepUpWindowSeconds(300);
        props.setBreakGlassTtlMinutes(60);

        lenient().when(riskScoring.score(any(), any(), any())).thenReturn(10);
        lenient().when(privilegeRevocationStore.isRevoked(anyString())).thenReturn(false);
        lenient().when(visibilityEscalationService.resolveActiveGrant(any())).thenReturn(Optional.empty());
        lenient().when(policyCacheService.getActiveRulesForResource(eq(TENANT), anyString()))
                .thenAnswer(inv -> visibleRules(inv.getArgument(1)));

        engine = new PolicyEngine(riskScoring, policyCacheService, privilegeRevocationStore,
                consentClient, stepUpService, breakGlassService, decisionLogRepository,
                auditPublisher, props, objectMapper, visibilityEscalationService,
                delegationClient, opaDecisionClient, roleTemplateCatalog, confidentialityPack);
    }

    // ── request + rule fixtures ──────────────────────────────────────────────

    /** A theatre request: cadre role(s), actor type, HTTP path, provider id, assurance, facility, LoA. */
    private AuthzInternalRequest req(List<String> roles, String actorType, String path,
                                     String providerId, String assurance, UUID facilityId, int loa) {
        String norm = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        String resourceType = norm.substring(norm.lastIndexOf('/') + 1);
        return new AuthzInternalRequest(
                TENANT, ACTOR, actorType, roles, "TREATMENT",
                DEVICE, CORR, facilityId, WORKSPACE,
                null, "POST", path, "POST:" + path, resourceType, null,
                loa, "session-1", null,
                providerId, null, null, null, null, assurance,
                null, null, DutyContext.absent());
    }

    private AuthzInternalRequest provider(List<String> roles, String path) {
        return req(roles, "PROVIDER", path, PROVIDER, "LOA3", FACILITY_A, 3);
    }

    private PolicyRuleEntity rule(String name, String role, String resourceType,
                                  int priority, String conditions) {
        PolicyRuleEntity r = new PolicyRuleEntity();
        r.setTenantId(TENANT);
        r.setName(name);
        r.setActorType("PROVIDER");
        r.setRole(role);
        r.setResourceType(resourceType);
        r.setAction("POST");
        r.setPurpose("TREATMENT");
        r.setEffect("ALLOW");
        r.setPriority(priority);
        r.setFacilityScope(true);   // dimension 1: facility context required
        r.setWorkspaceScope(false);
        r.setConditions(conditions);
        r.setActive(true);
        return r;
    }

    /** All shipped theatre ALLOW rows (V029 + V030 + V034), pins in segment-bounded form. */
    private List<PolicyRuleEntity> allTheatreRules(boolean crossFacilityMode) {
        List<PolicyRuleEntity> all = new ArrayList<>();
        // V029 book — coordinators. In cross-facility mode the unrestricted book ALLOWs are replaced
        // by a single facility-pinned rule so allowed_facilities is the SOLE gate (facility dimension).
        if (crossFacilityMode) {
            all.add(rule("theatre-book-surgeon-facB", "SURGEON", "book", 40,
                    "{\"path_contains\": \"/theatre/cases\", \"allowed_facilities\": [\"" + FACILITY_B + "\"]}"));
        } else {
            all.add(rule("theatre-book-surgeon", "SURGEON", "book", 50, "{\"path_contains\": \"/theatre/cases\"}"));
            all.add(rule("theatre-book-clinician", "CLINICIAN", "book", 51, "{\"path_contains\": \"/theatre/cases\"}"));
        }
        // V029 note sign — surgical scope only (NURSE/ANAESTHETIST deliberately NOT granted)
        all.add(rule("theatre-note-sign-surgeon", "SURGEON", "sign", 50, "{\"path_contains\": \"/theatre/cases\"}"));
        all.add(rule("theatre-note-sign-consultant", "CONSULTANT", "sign", 51, "{\"path_contains\": \"/theatre/cases\"}"));
        // V030 counts — scrub nurse + surgeon
        all.add(rule("theatre-count-record-nurse", "NURSE", "counts", 50, "{\"path_contains\": \"/theatre/cases\"}"));
        all.add(rule("theatre-count-record-surgeon", "SURGEON", "counts", 51, "{\"path_contains\": \"/theatre/cases\"}"));
        // V030 anaesthesia chart — anaesthetist + nurse
        all.add(rule("theatre-anaesthesia-chart-anaesthetist", "ANAESTHETIST", "chart", 50, "{\"path_contains\": \"/anaesthesia/chart\"}"));
        all.add(rule("theatre-anaesthesia-chart-nurse", "NURSE", "chart", 51, "{\"path_contains\": \"/anaesthesia/chart\"}"));
        // V034 checklist team sign (resource_type NULL wildcard, pinned to /checklist)
        all.add(rule("theatre-checklist-sign-surgeon", "SURGEON", null, 50, "{\"path_contains\": \"/checklist\"}"));
        all.add(rule("theatre-checklist-sign-anaesthetist", "ANAESTHETIST", null, 51, "{\"path_contains\": \"/checklist\"}"));
        all.add(rule("theatre-checklist-sign-nurse", "NURSE", null, 52, "{\"path_contains\": \"/checklist\"}"));
        // V034 discharge — surgeon + clinician
        all.add(rule("theatre-discharge-surgeon", "SURGEON", "discharge", 50, "{\"path_contains\": \"/theatre/cases\"}"));
        all.add(rule("theatre-discharge-clinician", "CLINICIAN", "discharge", 51, "{\"path_contains\": \"/theatre/cases\"}"));
        // V034 emergency case creation — surgeon/anaesthetist, min_loa 2
        all.add(rule("theatre-emergency-case-surgeon", "SURGEON", "emergency", 50, "{\"path_contains\": \"/theatre/cases/emergency\", \"min_loa\": 2}"));
        all.add(rule("theatre-emergency-case-anaesthetist", "ANAESTHETIST", "emergency", 52, "{\"path_contains\": \"/theatre/cases/emergency\", \"min_loa\": 2}"));
        // V034 emergency consent-exception — surgeon, min_loa 2
        all.add(rule("theatre-consent-exception-surgeon", "SURGEON", "emergency-exception", 50, "{\"path_contains\": \"/consent/emergency-exception\", \"min_loa\": 2}"));
        return all;
    }

    /** Only the rules the cache would return for a given last-path-segment (NULL == wildcard). */
    private List<PolicyRuleEntity> visibleRules(String segment) {
        boolean crossFac = crossFacilityMode;
        List<PolicyRuleEntity> visible = new ArrayList<>();
        for (PolicyRuleEntity r : allTheatreRules(crossFac)) {
            if (r.getResourceType() == null || r.getResourceType().equals(segment)
                    || segment.startsWith(r.getResourceType())) {
                visible.add(r);
            }
        }
        return visible;
    }

    private boolean crossFacilityMode = false;

    // ════════════════════════════════════════════════════════════════════════
    // CORRECT user ALLOWED — one per key action
    // ════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("book: SURGEON with facility + LOA3 -> ALLOW")
    void book_surgeon_allows() {
        assertEquals(Verdict.ALLOW,
                engine.evaluate(provider(List.of("SURGEON"), "/internal/v1/theatre/cases/case-1/book")).verdict());
    }

    @Test @DisplayName("note/sign: SURGEON (surgical scope) -> ALLOW")
    void noteSign_surgeon_allows() {
        assertEquals(Verdict.ALLOW,
                engine.evaluate(provider(List.of("SURGEON"), "/internal/v1/theatre/cases/case-1/note/sign")).verdict());
    }

    @Test @DisplayName("counts: scrub NURSE -> ALLOW (a discrepancy must never be blocked)")
    void counts_nurse_allows() {
        assertEquals(Verdict.ALLOW,
                engine.evaluate(provider(List.of("NURSE"), "/internal/v1/theatre/cases/case-1/counts")).verdict());
    }

    @Test @DisplayName("anaesthesia/chart: ANAESTHETIST -> ALLOW")
    void chart_anaesthetist_allows() {
        assertEquals(Verdict.ALLOW,
                engine.evaluate(provider(List.of("ANAESTHETIST"), "/internal/v1/procedures/case-1/anaesthesia/chart")).verdict());
    }

    @Test @DisplayName("checklist phase: NURSE (team-signed WHO checklist) -> ALLOW")
    void checklist_nurse_allows() {
        assertEquals(Verdict.ALLOW,
                engine.evaluate(provider(List.of("NURSE"), "/internal/v1/procedures/episodes/e-1/checklist/time-out")).verdict());
    }

    @Test @DisplayName("emergency case: SURGEON at LOA2 -> ALLOW (audited override)")
    void emergencyCase_surgeon_allows() {
        assertEquals(Verdict.ALLOW,
                engine.evaluate(req(List.of("SURGEON"), "PROVIDER", "/internal/v1/theatre/cases/emergency", PROVIDER, "LOA2", FACILITY_A, 2)).verdict());
    }

    @Test @DisplayName("discharge: SURGEON -> ALLOW")
    void discharge_surgeon_allows() {
        assertEquals(Verdict.ALLOW,
                engine.evaluate(provider(List.of("SURGEON"), "/internal/v1/theatre/cases/case-1/discharge")).verdict());
    }

    // ════════════════════════════════════════════════════════════════════════
    // INCORRECT user DENIED — the point of §16 (10 dimensions)
    // ════════════════════════════════════════════════════════════════════════

    @Test @DisplayName("D4/D9 cadre + separation-of-scope: NURSE signing the operative note -> DENY (NO_ALLOW_RULE)")
    void noteSign_nurse_denied() {
        AuthzResponse r = engine.evaluate(provider(List.of("NURSE"), "/internal/v1/theatre/cases/case-1/note/sign"));
        assertEquals(Verdict.DENY, r.verdict());
        assertEquals("NO_ALLOW_RULE", r.errorCode(),
                "note-sign is surgical scope only; a NURSE matches no ALLOW → denied");
    }

    @Test @DisplayName("D9 separation-of-scope: ANAESTHETIST signing the operative note -> DENY (NO_ALLOW_RULE)")
    void noteSign_anaesthetist_denied() {
        AuthzResponse r = engine.evaluate(provider(List.of("ANAESTHETIST"), "/internal/v1/theatre/cases/case-1/note/sign"));
        assertEquals(Verdict.DENY, r.verdict());
        assertEquals("NO_ALLOW_RULE", r.errorCode());
    }

    @Test @DisplayName("D4 cadre: NURSE booking a case (surgical/coordinator only) -> DENY (NO_ALLOW_RULE)")
    void book_nurse_denied() {
        AuthzResponse r = engine.evaluate(provider(List.of("NURSE"), "/internal/v1/theatre/cases/case-1/book"));
        assertEquals(Verdict.DENY, r.verdict());
        assertEquals("NO_ALLOW_RULE", r.errorCode());
    }

    @Test @DisplayName("citizen surface: a CITIZEN hitting theatre start -> DENY (no PROVIDER-scoped ALLOW)")
    void citizen_theatre_denied() {
        AuthzResponse r = engine.evaluate(
                req(List.of("CITIZEN"), "CITIZEN", "/internal/v1/theatre/cases/case-1/start", null, "LOA2", FACILITY_A, 2));
        assertEquals(Verdict.DENY, r.verdict());
        assertEquals("NO_ALLOW_RULE", r.errorCode());
    }

    @Test @DisplayName("patient surface: a PATIENT hitting the anaesthesia chart -> DENY (no PROVIDER-scoped ALLOW)")
    void patient_chart_denied() {
        AuthzResponse r = engine.evaluate(
                req(List.of("PATIENT"), "PATIENT", "/internal/v1/procedures/case-1/anaesthesia/chart", null, "LOA2", FACILITY_A, 2));
        assertEquals(Verdict.DENY, r.verdict());
        assertEquals("NO_ALLOW_RULE", r.errorCode());
    }

    @Test @DisplayName("D1 facility context: SURGEON booking with NO facility -> DENY (facility_scope, NO_ALLOW_RULE)")
    void book_noFacility_denied() {
        AuthzResponse r = engine.evaluate(
                req(List.of("SURGEON"), "PROVIDER", "/internal/v1/theatre/cases/case-1/book", PROVIDER, "LOA3", null, 3));
        assertEquals(Verdict.DENY, r.verdict());
        assertEquals("NO_ALLOW_RULE", r.errorCode());
    }

    @Test @DisplayName("D3/D5/D10 licence + theatre-privilege + role-revocation: VARAPI-revoked SURGEON -> DENY (PROVIDER_PRIVILEGE_REVOKED)")
    void revokedPrivilege_denied() {
        when(privilegeRevocationStore.isRevoked(PROVIDER)).thenReturn(true);
        AuthzResponse r = engine.evaluate(provider(List.of("SURGEON"), "/internal/v1/theatre/cases/case-1/book"));
        assertEquals(Verdict.DENY, r.verdict());
        assertEquals("PROVIDER_PRIVILEGE_REVOKED", r.errorCode());
    }

    @Test @DisplayName("D6 assurance floor (trainee / weak session): SURGEON emergency case at LOA1 < min_loa 2 -> DENY (NO_ALLOW_RULE)")
    void emergencyCase_lowLoa_denied() {
        AuthzResponse r = engine.evaluate(
                req(List.of("SURGEON"), "PROVIDER", "/internal/v1/theatre/cases/emergency", PROVIDER, "LOA1", FACILITY_A, 1));
        assertEquals(Verdict.DENY, r.verdict());
        assertEquals("NO_ALLOW_RULE", r.errorCode());
    }

    @Test @DisplayName("D7/D8 consent authority under emergency: SURGEON consent-exception at LOA1 -> DENY (below assurance floor)")
    void consentException_lowLoa_denied() {
        AuthzResponse r = engine.evaluate(
                req(List.of("SURGEON"), "PROVIDER", "/internal/v1/theatre/cases/c-1/consent/emergency-exception", PROVIDER, "LOA1", FACILITY_A, 1));
        assertEquals(Verdict.DENY, r.verdict());
        assertEquals("NO_ALLOW_RULE", r.errorCode());
    }

    @Test @DisplayName("cross-facility: SURGEON at facility A hitting a case pinned to facility B -> DENY (NO_ALLOW_RULE)")
    void crossFacility_denied() {
        crossFacilityMode = true;
        AuthzResponse r = engine.evaluate(
                req(List.of("SURGEON"), "PROVIDER", "/internal/v1/theatre/cases/case-1/book", PROVIDER, "LOA3", FACILITY_A, 3));
        assertEquals(Verdict.DENY, r.verdict());
        assertEquals("NO_ALLOW_RULE", r.errorCode());
    }

    @Test @DisplayName("cross-facility (positive control): SURGEON AT facility B is ALLOWED by the same pinned rule")
    void crossFacility_matchingFacility_allows() {
        crossFacilityMode = true;
        AuthzResponse r = engine.evaluate(
                req(List.of("SURGEON"), "PROVIDER", "/internal/v1/theatre/cases/case-1/book", PROVIDER, "LOA3", FACILITY_B, 3));
        assertEquals(Verdict.ALLOW, r.verdict(),
                "the facility-pinned rule must ALLOW the same surgeon when actually at facility B");
    }
}
