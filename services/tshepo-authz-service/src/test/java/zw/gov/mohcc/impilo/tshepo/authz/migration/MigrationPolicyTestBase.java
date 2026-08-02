package zw.gov.mohcc.impilo.tshepo.authz.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.core.DecisionEnvelopeSigner;
import zw.gov.mohcc.impilo.tshepo.authz.core.DeviceRiskScoreEvaluator;
import zw.gov.mohcc.impilo.tshepo.authz.core.PolicyEngine;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.DutyContext;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.PolicyDecisionLogRepository;
import zw.gov.mohcc.impilo.tshepo.authz.service.*;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthzResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.WorkMode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Runs seeded policy rules through the real {@link PolicyEngine}.
 *
 * <p>Policy rules are data, and data written into a migration is only ever read by the
 * engine — never by a compiler and rarely by a second pair of eyes. A test that restates
 * what a rule is believed to do inherits whatever the author misunderstood. Executing the
 * migration's own text is the only version of this test that can disagree with its author,
 * and the trailing-slash defect in V055 is what it found the first time it was run.</p>
 */
@ExtendWith(MockitoExtension.class)
abstract class MigrationPolicyTestBase {

    /** Real registry: the shadow metrics are asserted, not stubbed away. */
    protected final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    protected static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    protected static final UUID CORRELATION_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    protected static final UUID FACILITY_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    protected static final String ACTOR_ID = "actor-123";
    protected static final String DEVICE_FP = "fp-sha256-abcdef";

    protected static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

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

    @BeforeEach
    void wirePolicyEngine() {
        AuthzProperties props = new AuthzProperties();
        AuthzProperties.RiskThresholds thresholds = new AuthzProperties.RiskThresholds();
        thresholds.setDenyThreshold(81);
        thresholds.setStepUpTrigger(61);
        thresholds.setUnknownDeviceScore(70);
        thresholds.setNewDeviceScore(50);
        thresholds.setBlockedDeviceScore(100);
        props.setRiskThresholds(thresholds);
        props.setStepUpMethods(List.of("MFA"));
        props.setStepUpWindowSeconds(300);
        props.setBreakGlassTtlMinutes(60);

        lenient().when(privilegeRevocationStore.isRevoked(anyString())).thenReturn(false);
        lenient().when(visibilityEscalationService.resolveActiveGrant(any())).thenReturn(Optional.empty());
        lenient().when(confidentialityPack.isEffective()).thenReturn(false);
        lenient().when(confidentialityPack.retainGrantable(anyList())).thenReturn(Set.of());
        lenient().when(confidentialityPack.stateLabel()).thenReturn("INERT");
        lenient().when(confidentialityPack.packId()).thenReturn("pack");
        lenient().when(confidentialityPack.version()).thenReturn("1.0.0");
        lenient().when(riskScoring.score(eq(TENANT_ID), eq(DEVICE_FP), eq(ACTOR_ID))).thenReturn(10);

        policyEngine = new PolicyEngine(
                riskScoring, policyCacheService, privilegeRevocationStore, consentClient,
                stepUpService, breakGlassService, decisionLogRepository,
                auditPublisher, props, new ObjectMapper(), visibilityEscalationService,
                delegationClient, opaDecisionClient, roleTemplateCatalog, confidentialityPack,
                decisionEnvelopeSigner, meterRegistry);
    }

    /**
     * Evaluate {@code path} against a single DENY rule carrying {@code conditions}, with a
     * permissive ALLOW loaded beside it.
     *
     * <p>The ALLOW is not decoration. Without one the request is refused by NO_ALLOW_RULE and
     * every "must be denied" assertion passes for a reason that has nothing to do with the
     * rule under test — the exact way a boundary test can look green while proving nothing.</p>
     */
    protected AuthzResponse decideWithDeny(String conditions, String path, DutyContext duty) {
        PolicyRuleEntity deny = rule("boundary-rule-under-test", "DENY", 54, conditions);
        PolicyRuleEntity allow = rule("baseline-allow", "ALLOW", 100, null);

        when(policyCacheService.getActiveRulesForResource(eq(TENANT_ID), anyString()))
                .thenReturn(List.of(deny, allow));

        return policyEngine.evaluate(new AuthzInternalRequest(
                TENANT_ID, ACTOR_ID, "PROVIDER", List.of("CLINICIAN"), "TREATMENT",
                DEVICE_FP, CORRELATION_ID, FACILITY_ID, null,
                null, "GET", path, "GET:" + path, "record", null,
                3, "session-abc", null,
                null, null, null, null, null, null,
                null, null, duty));
    }

    private PolicyRuleEntity rule(String name, String effect, int priority, String conditions) {
        PolicyRuleEntity rule = new PolicyRuleEntity();
        rule.setTenantId(TENANT_ID);
        rule.setName(name);
        rule.setEffect(effect);
        rule.setPriority(priority);
        rule.setActive(true);
        rule.setFacilityScope(false);
        rule.setWorkspaceScope(false);
        rule.setConditions(conditions);
        return rule;
    }

    /** A usable WORK_CONTEXT duty token in the given mode, with that mode's own access envelope. */
    protected static DutyContext dutyIn(WorkMode mode) {
        return new DutyContext(true, true, "WORK_CONTEXT", ACTOR_ID,
                FACILITY_ID.toString(), null, null, null, null, "org-1", null,
                "SOME_TEMPLATE", null, null,
                mode.name(), mode.clinicalDataAccess().name(), null, null);
    }

    protected static String readMigration(String fileName) throws IOException {
        Path path = MIGRATIONS.resolve(fileName);
        assertThat(path).exists();
        return Files.readString(path);
    }

    /**
     * Every rule name in a migration mapped to its conditions JSON, so a test can run the
     * text that will actually be applied.
     */
    protected static Map<String, String> conditionsByRuleName(String sql) {
        Pattern row = Pattern.compile("'([a-z0-9-]+)',\\s*\\n.*?'(\\{.*?})',", Pattern.DOTALL);
        Matcher m = row.matcher(sql);

        Map<String, String> byName = new LinkedHashMap<>();
        while (m.find()) {
            byName.put(m.group(1), m.group(2));
        }
        assertThat(byName)
                .as("parsed no rules — the test would pass vacuously")
                .isNotEmpty();
        return byName;
    }
}
