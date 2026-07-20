package zw.gov.mohcc.impilo.experience.trust;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;
import zw.gov.mohcc.impilo.experience.client.ProviderTrustClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Four-block trust profile composition (IATG WS-D): each block cites its system
 * of record and degrades independently to {"status":"UNAVAILABLE"} when its
 * downstream fails — never fabricating, never letting one outage poison the
 * other blocks — and EC numbers are always masked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrustProfileComposerTest {

    private static final String HEALTH_ID = "HID-42";
    private static final String PROVIDER_PUBLIC_ID = "ZXCV9876";
    private static final String RAW_EC = "EC555777";

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock private IdentityAssuranceServiceClient identityAssuranceClient;
    @Mock private VarapiServiceClient varapiServiceClient;
    @Mock private ProviderTrustClient providerTrustClient;
    @Mock private WorkforceGovernanceClient workforceGovernanceClient;
    @Mock private VashandiServiceClient vashandiServiceClient;

    private TrustProfileComposer composer;

    @BeforeEach
    void setUp() {
        composer = new TrustProfileComposer(identityAssuranceClient, varapiServiceClient,
                providerTrustClient, workforceGovernanceClient, vashandiServiceClient, mapper);
        wireHappyDefaults();
    }

    private void wireHappyDefaults() {
        ObjectNode assurance = mapper.createObjectNode().put("assuranceLevel", "LOA2");
        when(identityAssuranceClient.getAssuranceStatus()).thenReturn(assurance);

        when(varapiServiceClient.rawGet(anyString())).thenReturn(ResponseEntity.ok(
                "{\"data\":{\"providerPublicId\":\"" + PROVIDER_PUBLIC_ID + "\"}}"));

        ObjectNode trust = mapper.createObjectNode()
                .put("trustLevel", "EMPLOYMENT_MATCHED")
                .put("registryStatus", "ACTIVE")
                .put("onboardingChannel", "WORKFORCE_B");
        ArrayNode attempts = trust.putArray("attempts");
        ObjectNode attempt = attempts.addObject();
        attempt.put("type", "EC_MATCH").put("source", "HSC_EMPLOYMENT")
                .put("outcome", "ACCEPTED").put("confidence", 0.95).put("ref", "EC***");
        when(providerTrustClient.getTrust(PROVIDER_PUBLIC_ID)).thenReturn(trust);

        ArrayNode records = mapper.createArrayNode();
        ObjectNode record = records.addObject();
        record.put("employmentStatus", "ACTIVE").put("cadre", "NURSE").put("grade", "D1")
                .put("verificationStatus", "VERIFIED").put("ecNumber", RAW_EC);
        when(workforceGovernanceClient.getJson(anyString())).thenReturn(records);

        ObjectNode workContext = mapper.createObjectNode();
        workContext.putArray("assignments").addObject().put("facilityId", "FAC-1");
        workContext.put("requiresContextChooser", false);
        when(vashandiServiceClient.fetchWorkContext(HEALTH_ID)).thenReturn(workContext);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(Map<String, Object> profile, String name) {
        return (Map<String, Object>) profile.get(name);
    }

    @Test
    void happyPathComposesFourBlocksEachCitingItsSourceOfRecord() {
        Map<String, Object> profile = composer.compose(HEALTH_ID, null);

        // PII Wave 0.3 stage 1c: the composer must NOT echo the raw Health ID to the
        // browser — the healthId is server-side lookup input only.
        assertThat(profile).doesNotContainKey("healthId");
        assertThat(profile).containsKey("generatedAt");
        assertThat(block(profile, "identity"))
                .containsEntry("status", "OK")
                .containsEntry("sourceOfRecord", "identity-assurance-service");
        assertThat(block(profile, "professional"))
                .containsEntry("status", "OK")
                .containsEntry("providerId", PROVIDER_PUBLIC_ID)
                .containsEntry("trustLevel", "EMPLOYMENT_MATCHED")
                .containsEntry("sourceOfRecord", "varapi-service (provider registry trust ledger)");
        assertThat(block(profile, "employment"))
                .containsEntry("status", "OK")
                .containsEntry("recordCount", 1)
                .containsEntry("sourceOfRecord", "workforce-governance-service (wgv_hsc_employment)");
        assertThat(block(profile, "operational"))
                .containsEntry("status", "OK")
                .containsEntry("activeAssignmentCount", 1)
                .containsEntry("sourceOfRecord", "vashandi-workforce-service (work-context read model)");
    }

    @Test
    void identityOutageDegradesOnlyIdentityBlock() {
        when(identityAssuranceClient.getAssuranceStatus()).thenThrow(new RuntimeException("boom"));

        Map<String, Object> profile = composer.compose(HEALTH_ID, null);

        assertThat(block(profile, "identity")).containsEntry("status", "UNAVAILABLE");
        assertThat(block(profile, "professional")).containsEntry("status", "OK");
        assertThat(block(profile, "employment")).containsEntry("status", "OK");
        assertThat(block(profile, "operational")).containsEntry("status", "OK");
    }

    @Test
    void providerRegistryOutageDegradesOnlyProfessionalBlock() {
        when(varapiServiceClient.rawGet(anyString()))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        Map<String, Object> profile = composer.compose(HEALTH_ID, null);

        assertThat(block(profile, "professional")).containsEntry("status", "UNAVAILABLE");
        assertThat(block(profile, "identity")).containsEntry("status", "OK");
        assertThat(block(profile, "employment")).containsEntry("status", "OK");
        assertThat(block(profile, "operational")).containsEntry("status", "OK");
    }

    @Test
    void noProviderRegistrationIsHonestlyReportedNotFabricated() {
        when(varapiServiceClient.rawGet(anyString())).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        HttpHeaders.EMPTY, new byte[0], null));

        Map<String, Object> profile = composer.compose(HEALTH_ID, null);

        Map<String, Object> professional = block(profile, "professional");
        assertThat(professional).containsEntry("status", "NO_PROVIDER_LINKED");
        assertThat(professional).containsEntry("providerCreationRequired", true);
        assertThat(professional).doesNotContainKey("trustLevel");
    }

    @Test
    void trustLedgerOutageAfterResolutionDegradesProfessionalBlock() {
        when(providerTrustClient.getTrust(PROVIDER_PUBLIC_ID)).thenReturn(null);

        Map<String, Object> profile = composer.compose(HEALTH_ID, null);

        Map<String, Object> professional = block(profile, "professional");
        assertThat(professional).containsEntry("status", "UNAVAILABLE");
        assertThat(professional).containsEntry("providerId", PROVIDER_PUBLIC_ID);
    }

    @Test
    void explicitProviderIdOverrideSkipsHealthIdResolution() {
        when(varapiServiceClient.rawGet(anyString())).thenThrow(new RuntimeException("must not be called"));
        ObjectNode trust = mapper.createObjectNode().put("trustLevel", "COUNCIL_VERIFIED");
        when(providerTrustClient.getTrust("OVERRIDE-1")).thenReturn(trust);

        Map<String, Object> profile = composer.compose(HEALTH_ID, "OVERRIDE-1");

        Map<String, Object> professional = block(profile, "professional");
        assertThat(professional).containsEntry("status", "OK");
        assertThat(professional).containsEntry("providerId", "OVERRIDE-1");
        assertThat(professional).containsEntry("trustLevel", "COUNCIL_VERIFIED");
    }

    @Test
    void governanceOutageDegradesOnlyEmploymentBlock() {
        when(workforceGovernanceClient.getJson(anyString())).thenReturn(null);

        Map<String, Object> profile = composer.compose(HEALTH_ID, null);

        assertThat(block(profile, "employment")).containsEntry("status", "UNAVAILABLE");
        assertThat(block(profile, "identity")).containsEntry("status", "OK");
        assertThat(block(profile, "professional")).containsEntry("status", "OK");
        assertThat(block(profile, "operational")).containsEntry("status", "OK");
    }

    @Test
    void vashandiOutageDegradesOnlyOperationalBlock() {
        when(vashandiServiceClient.fetchWorkContext(HEALTH_ID)).thenReturn(null);

        Map<String, Object> profile = composer.compose(HEALTH_ID, null);

        assertThat(block(profile, "operational")).containsEntry("status", "UNAVAILABLE");
        assertThat(block(profile, "identity")).containsEntry("status", "OK");
        assertThat(block(profile, "professional")).containsEntry("status", "OK");
        assertThat(block(profile, "employment")).containsEntry("status", "OK");
    }

    @Test
    void everyDownstreamDownStillReturnsFourHonestBlocks() {
        when(identityAssuranceClient.getAssuranceStatus()).thenThrow(new RuntimeException("down"));
        when(varapiServiceClient.rawGet(anyString())).thenThrow(new RuntimeException("down"));
        when(workforceGovernanceClient.getJson(anyString())).thenReturn(null);
        when(vashandiServiceClient.fetchWorkContext(HEALTH_ID)).thenReturn(null);

        Map<String, Object> profile = composer.compose(HEALTH_ID, null);

        assertThat(block(profile, "identity")).containsEntry("status", "UNAVAILABLE");
        assertThat(block(profile, "professional")).containsEntry("status", "UNAVAILABLE");
        assertThat(block(profile, "employment")).containsEntry("status", "UNAVAILABLE");
        assertThat(block(profile, "operational")).containsEntry("status", "UNAVAILABLE");
    }

    @Test
    void ecNumberIsMaskedAndNeverLeaksInTheProfile() throws Exception {
        Map<String, Object> profile = composer.compose(HEALTH_ID, null);

        String serialized = mapper.writeValueAsString(profile);
        assertThat(serialized).doesNotContain(RAW_EC);
        assertThat(serialized).contains("EC***");
    }
}
