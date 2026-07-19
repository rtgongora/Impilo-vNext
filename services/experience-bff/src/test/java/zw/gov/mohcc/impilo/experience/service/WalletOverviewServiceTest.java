package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoConsentServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Unified Person Health Wallet overview composition. The wallet owns no data —
 * these tests verify it (a) delegates to the sovereign owners, (b) degrades each card to a safe
 * empty state when a downstream fails (never failing the whole wallet), and (c) derives graduated
 * next-step prompts from the trust state.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletOverviewServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock private VitoServiceClient vitoClient;
    @Mock private IdentityAssuranceServiceClient assuranceClient;
    @Mock private CitizenHealthSummaryService healthSummaryService;
    @Mock private PctServiceClient pctClient;
    @Mock private TshepoConsentServiceClient consentClient;
    @Mock private MvumoServiceClient mvumoClient;
    @Mock private CostaServiceClient costaClient;
    @Mock private NotificationServiceClient notificationClient;
    @Mock private GuidanceServiceClient guidanceClient;

    private WalletOverviewService service() {
        return new WalletOverviewService(vitoClient, assuranceClient, healthSummaryService, pctClient,
                consentClient, mvumoClient, costaClient, notificationClient, guidanceClient);
    }

    private void wireHappyDefaults() {
        ObjectNode assurance = mapper.createObjectNode().put("assuranceState", "FULLY_VERIFIED");
        ObjectNode profile = mapper.createObjectNode()
                .put("givenName", "Tendai").put("familyName", "Moyo")
                .put("dateOfBirth", "1990-01-01").put("sex", "F")
                .put("phone", "+263770000000").put("address", "Harare")
                .put("impiloId", "IMP-123");
        lenient().when(assuranceClient.getAssuranceStatus()).thenReturn(assurance);
        lenient().when(vitoClient.getCitizenProfile(anyString())).thenReturn(profile);
        lenient().when(healthSummaryService.buildSummary(anyString())).thenReturn(Map.of("conditions", List.of()));
        lenient().when(pctClient.getPatientTimeline(anyString())).thenReturn(mapper.createArrayNode());
        lenient().when(consentClient.listConsents(anyString(), anyString(), anyInt(), anyInt())).thenReturn(mapper.createArrayNode());
        lenient().when(mvumoClient.listDelegationsForDelegate(anyString())).thenReturn(mapper.createArrayNode());
        lenient().when(mvumoClient.listDelegationsForSubject(anyString())).thenReturn(mapper.createArrayNode());
        lenient().when(costaClient.listBills(anyInt(), anyInt(), anyString())).thenReturn(mapper.createArrayNode());
        lenient().when(notificationClient.getPreferences(anyString())).thenReturn(mapper.createObjectNode());
        lenient().when(guidanceClient.getReminders()).thenReturn(mapper.createArrayNode());
    }

    @Test
    void composesEverySectionFromTheSovereignOwners() {
        wireHappyDefaults();

        Map<String, Object> data = service().buildOverview("CPID-1");

        assertThat(data).containsKeys("identity", "profileCompletion", "consent", "dependants",
                "records", "careTimeline", "payments", "commsPreferences", "guidance", "nextActions");
        Map<?, ?> identity = (Map<?, ?>) data.get("identity");
        // D7: the raw Health ID (internal UUID) is never surfaced to the browser; the
        // patient-facing Impilo ID (from the profile) is exposed instead.
        assertThat(identity.get("healthId")).isNull();
        assertThat(identity.get("impiloId")).isEqualTo("IMP-123");
        assertThat(identity.get("_source")).asString().contains("vito").contains("identity-assurance");
    }

    @Test
    void profileCompletionReportsFullWhenAllFieldsPresent() {
        wireHappyDefaults();
        Map<String, Object> data = service().buildOverview("CPID-1");
        Map<?, ?> completion = (Map<?, ?>) data.get("profileCompletion");
        assertThat(completion.get("percent")).isEqualTo(100);
        assertThat((List<?>) completion.get("missing")).isEmpty();
    }

    @Test
    void profileCompletionFlagsMissingFieldsAndEmitsCompleteProfileAction() {
        wireHappyDefaults();
        // Partial profile: missing phone + address.
        ObjectNode partial = mapper.createObjectNode()
                .put("givenName", "Tendai").put("familyName", "Moyo")
                .put("dateOfBirth", "1990-01-01").put("sex", "F");
        when(vitoClient.getCitizenProfile("CPID-1")).thenReturn(partial);

        Map<String, Object> data = service().buildOverview("CPID-1");

        Map<?, ?> completion = (Map<?, ?>) data.get("profileCompletion");
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) completion.get("missing");
        assertThat(missing).contains("phone", "address");
        assertThat((int) completion.get("percent")).isLessThan(100);
        assertThat(actionCodes(data)).contains("COMPLETE_PROFILE");
    }

    @Test
    void lowTrustEmitsUpgradeTrustNextAction() {
        wireHappyDefaults();
        ObjectNode selfReg = mapper.createObjectNode().put("assuranceState", "SELF_REGISTERED");
        when(assuranceClient.getAssuranceStatus()).thenReturn(selfReg);

        Map<String, Object> data = service().buildOverview("CPID-1");

        assertThat(actionCodes(data)).contains("UPGRADE_TRUST");
    }

    @Test
    void outstandingBillsEmitSettleBillNextActionWithCount() {
        wireHappyDefaults();
        ArrayNode bills = mapper.createArrayNode();
        bills.add(mapper.createObjectNode().put("id", "BILL-1").put("status", "FINAL"));
        when(costaClient.listBills(anyInt(), anyInt(), anyString())).thenReturn(bills);

        Map<String, Object> data = service().buildOverview("CPID-1");

        Map<?, ?> payments = (Map<?, ?>) data.get("payments");
        assertThat(payments.get("outstandingCount")).isEqualTo(1);
        assertThat(actionCodes(data)).contains("SETTLE_BILL");
    }

    @Test
    void outstandingBillsAreQueriedWithAValidCostaBillStatus() {
        wireHappyDefaults();

        service().buildOverview("CPID-1");

        // "INVOICED" is not a COSTA BillStatus; querying with it made this card
        // permanently unavailable. The wallet must ask for FINAL bills.
        org.mockito.Mockito.verify(costaClient).listBills(anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq("FINAL"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void downstreamFailureDegradesOneCardButWalletStillBuilds() {
        wireHappyDefaults();
        // Records, consent and payments owners are down — only those cards degrade.
        when(healthSummaryService.buildSummary(anyString())).thenThrow(new RuntimeException("butano down"));
        when(consentClient.listConsents(anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("consent down"));
        when(costaClient.listBills(anyInt(), anyInt(), anyString())).thenThrow(new RuntimeException("costa down"));

        Map<String, Object> data = service().buildOverview("CPID-1");

        assertThat(data).containsKeys("records", "consent", "payments", "identity");
        assertThat((Map<String, Object>) data.get("records")).containsEntry("unavailable", true);
        assertThat((Map<String, Object>) data.get("consent")).containsEntry("unavailable", true);
        assertThat((Map<String, Object>) data.get("payments")).containsEntry("unavailable", true);
        // Identity card still present and healthy.
        assertThat((Map<String, Object>) data.get("identity")).doesNotContainKey("profileUnavailable");
    }

    @Test
    void dependantSummaryCountsBothDirections() {
        wireHappyDefaults();
        ArrayNode actsFor = mapper.createArrayNode();
        actsFor.add(mapper.createObjectNode().put("subjectRef", "Patient/CHILD-1"));
        when(mvumoClient.listDelegationsForDelegate("CPID-1")).thenReturn(actsFor);

        Map<String, Object> data = service().buildOverview("CPID-1");

        Map<?, ?> dependants = (Map<?, ?>) data.get("dependants");
        assertThat(dependants.get("iCanActForCount")).isEqualTo(1);
        assertThat(dependants.get("canActForMeCount")).isEqualTo(0);
    }

    @SuppressWarnings("unchecked")
    private List<String> actionCodes(Map<String, Object> data) {
        List<Map<String, Object>> actions = (List<Map<String, Object>>) data.get("nextActions");
        return actions.stream().map(a -> (String) a.get("code")).toList();
    }
}
