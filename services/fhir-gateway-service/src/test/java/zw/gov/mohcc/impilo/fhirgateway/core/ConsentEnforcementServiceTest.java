package zw.gov.mohcc.impilo.fhirgateway.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.fhirgateway.events.ConsentCacheService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The asymmetry between recording care and reading it.
 *
 * <p>tshepo-consent returns {@code permitted=false} in four different situations, and only one of
 * them is a person refusing. This service used to read nothing but {@code permitted}, so all four
 * refused. Measured 2026-08-08 against the deployed estate, that had denied <b>22 of 22</b>
 * non-break-glass forwards in the gateway's entire history — 20 of them telemonitoring
 * observations — while the only three that ever succeeded used BREAK_GLASS.</p>
 *
 * <p>These tests pin the rule that replaced it: a <b>write</b> proceeds when no directive speaks
 * to the point; a <b>read</b> does not. An explicit deny refuses both. Not knowing refuses both.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsentEnforcementServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String CPID = "c08ba747-26ff-4f19-b712-76561505e274";

    @Mock private RestTemplate restTemplate;
    @Mock private ConsentCacheService consentCacheService;

    private ConsentEnforcementService service;

    @BeforeEach
    void setUp() {
        service = new ConsentEnforcementService(restTemplate, "http://consent:8182", consentCacheService);
        when(consentCacheService.isConsentRevoked(anyString(), anyString())).thenReturn(false);
    }

    /** Shape the consent service actually returns: ApiResponse { data: ConsentDecision }. */
    private void consentReturns(String decisionJson) {
        try {
            JsonNode node = MAPPER.readTree("{\"data\":" + decisionJson + "}");
            when(restTemplate.getForObject(anyString(), any())).thenReturn(node);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private ConsentOutcome evaluate(String operation) {
        return service.evaluate("actor-1", CPID, "Observation", "TREATMENT", TENANT, operation);
    }

    // ── Absence of a directive ────────────────────────────────────────────────────────────────
    //
    // All three carry consentId = null. None of them is a refusal; each means "nothing on file
    // speaks to this". The exact reason string differs and must not matter.

    @ParameterizedTest
    @ValueSource(strings = {"NO_ACTIVE_CONSENT", "NO_MATCHING_CONSENT", "NO_VALID_CONSENT"})
    void write_proceeds_whenNoDirectiveSpeaksToThePoint(String reason) {
        consentReturns("{\"permitted\":false,\"reason\":\"" + reason + "\"}");

        assertThat(evaluate("CREATE"))
                .describedAs("%s is absence, not refusal — refusing the write loses the clinical "
                        + "fact silently and forever", reason)
                .isEqualTo(ConsentOutcome.PERMIT_NO_DIRECTIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NO_ACTIVE_CONSENT", "NO_MATCHING_CONSENT", "NO_VALID_CONSENT"})
    void read_isStillRefused_whenNoDirectivePermitsIt(String reason) {
        consentReturns("{\"permitted\":false,\"reason\":\"" + reason + "\"}");

        assertThat(evaluate("SEARCH"))
                .describedAs("reads stay fail-closed — consent governs who may see the record")
                .isEqualTo(ConsentOutcome.DENY);
    }

    // ── Explicit refusal ──────────────────────────────────────────────────────────────────────

    @Test
    void write_isRefused_byAnExplicitDenyDirective() {
        // A consentId on a refusal is the one shape that means a directive exists and says no.
        consentReturns("{\"permitted\":false,\"consentId\":\"3f1b2c0e-0000-4000-8000-000000000001\","
                + "\"provision\":\"deny\",\"reason\":\"CONSENT_DENIED\"}");

        assertThat(evaluate("CREATE")).isEqualTo(ConsentOutcome.DENY);
    }

    @Test
    void read_isRefused_byAnExplicitDenyDirective() {
        consentReturns("{\"permitted\":false,\"consentId\":\"3f1b2c0e-0000-4000-8000-000000000001\","
                + "\"provision\":\"deny\",\"reason\":\"CONSENT_DENIED\"}");

        assertThat(evaluate("SEARCH")).isEqualTo(ConsentOutcome.DENY);
    }

    @Test
    void write_isRefused_byARevocation() {
        // The Kafka-fed revocation cache is checked before the REST call and is unconditional.
        when(consentCacheService.isConsentRevoked(TENANT.toString(), CPID)).thenReturn(true);

        assertThat(evaluate("CREATE")).isEqualTo(ConsentOutcome.DENY);
    }

    // ── Not knowing is not absence ────────────────────────────────────────────────────────────

    @Test
    void write_isRefused_whenTheConsentServiceIsUnreachable() {
        when(restTemplate.getForObject(anyString(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        assertThat(evaluate("CREATE"))
                .describedAs("an unanswered question is not a measured absence; fail closed")
                .isEqualTo(ConsentOutcome.DENY);
    }

    @Test
    void write_isRefused_whenTheConsentServiceReturnsNothing() {
        when(restTemplate.getForObject(anyString(), any())).thenReturn(null);

        assertThat(evaluate("CREATE")).isEqualTo(ConsentOutcome.DENY);
    }

    // ── The operation vocabulary ──────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"CREATE", "UPDATE", "PUT", "POST", "PATCH", "create", " update "})
    void recordingOperationsGetTheRelaxedTreatment(String operation) {
        consentReturns("{\"permitted\":false,\"reason\":\"NO_ACTIVE_CONSENT\"}");

        assertThat(evaluate(operation)).isEqualTo(ConsentOutcome.PERMIT_NO_DIRECTIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELETE", "READ", "SEARCH", "VREAD", "HISTORY", "SOMETHING_NEW", ""})
    void everythingElseStaysFailClosed(String operation) {
        consentReturns("{\"permitted\":false,\"reason\":\"NO_ACTIVE_CONSENT\"}");

        assertThat(evaluate(operation))
                .describedAs("DELETE removes rather than records, and an operation nobody "
                        + "anticipated must not quietly acquire the relaxed treatment")
                .isEqualTo(ConsentOutcome.DENY);
    }

    @Test
    void aNullOperationIsTreatedAsARead() {
        consentReturns("{\"permitted\":false,\"reason\":\"NO_ACTIVE_CONSENT\"}");

        assertThat(evaluate(null)).isEqualTo(ConsentOutcome.DENY);
    }

    // ── Unchanged behaviour ───────────────────────────────────────────────────────────────────

    @Test
    void anActiveDirectiveStillPermits_andIsDistinguishableFromAbsence() {
        consentReturns("{\"permitted\":true,\"consentId\":\"3f1b2c0e-0000-4000-8000-000000000002\","
                + "\"provision\":\"permit\"}");

        assertThat(evaluate("CREATE")).isEqualTo(ConsentOutcome.PERMIT);
    }

    @Test
    void breakGlassStillBypasses_withoutCallingTheConsentService() {
        assertThat(service.evaluate("actor-1", CPID, "Observation", "BREAK_GLASS", TENANT, "CREATE"))
                .isEqualTo(ConsentOutcome.BREAK_GLASS_BYPASS);
    }

    @Test
    void conformanceResourcesAreStillExempt() {
        assertThat(service.evaluate("actor-1", CPID, "CapabilityStatement", "TREATMENT", TENANT, "CREATE"))
                .isEqualTo(ConsentOutcome.NOT_APPLICABLE);
    }

    @Test
    void aMissingSubjectIsStillNotApplicable() {
        assertThat(service.evaluate("actor-1", null, "Observation", "TREATMENT", TENANT, "CREATE"))
                .isEqualTo(ConsentOutcome.NOT_APPLICABLE);
    }
}
