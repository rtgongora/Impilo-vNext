package zw.gov.mohcc.impilo.experience.workhome;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfessionalAlertsComposerTest {

    private static final String ACTOR_ID = "health-id-1";
    private static final String PROVIDER_PUBLIC_ID = "provider-public-1";

    @Mock
    private VarapiServiceClient varapiClient;
    @Mock
    private WorkforceGovernanceClient governanceClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProfessionalAlertsComposer composer;

    private void stubProvider() {
        ObjectNode provider = objectMapper.createObjectNode().put("providerPublicId", PROVIDER_PUBLIC_ID);
        lenient().when(varapiClient.getProviderByHealthId(ACTOR_ID)).thenReturn(provider);
        lenient().when(varapiClient.getProviderLicenses(PROVIDER_PUBLIC_ID)).thenReturn(objectMapper.createArrayNode());
        lenient().when(varapiClient.getProviderCpdSummary(PROVIDER_PUBLIC_ID)).thenReturn(objectMapper.nullNode());
        lenient().when(governanceClient.searchAssignments("PROVIDER", PROVIDER_PUBLIC_ID, "PENDING"))
                .thenReturn(objectMapper.createArrayNode());
    }

    private WorkHomeSection run() {
        composer = new ProfessionalAlertsComposer(varapiClient, governanceClient);
        return composer.sectionTask(ACTOR_ID).supplier().get();
    }

    @Test
    void noProviderResolved_returnsEmptyWithoutCallingAnyOtherClient() {
        composer = new ProfessionalAlertsComposer(varapiClient, governanceClient);
        lenient().when(varapiClient.getProviderByHealthId(ACTOR_ID)).thenReturn(objectMapper.nullNode());

        WorkHomeSection section = composer.sectionTask(ACTOR_ID).supplier().get();

        assertThat(section.status()).isEqualTo("EMPTY");
    }

    @Test
    void suspendedLicence_producesUrgentAlert() {
        stubProvider();
        ObjectNode licence = objectMapper.createObjectNode()
                .put("id", "lic-1").put("licenseType", "Medical Practice").put("status", "SUSPENDED");
        when(varapiClient.getProviderLicenses(PROVIDER_PUBLIC_ID)).thenReturn(objectMapper.createArrayNode().add(licence));

        WorkHomeSection section = run();

        assertThat(section.status()).isEqualTo("OK");
        Map<String, Object> item = section.items().get(0);
        assertThat(item.get("kind")).isEqualTo("LICENCE_ALERT");
        assertThat(item.get("priority")).isEqualTo("URGENT");
        assertThat(item.get("status")).isEqualTo("SUSPENDED");
    }

    @Test
    void licenceExpiringWithin14Days_isUrgent_within60DaysIsHigh_beyond60DaysIsIgnored() {
        stubProvider();
        String soon = LocalDate.now().plusDays(10).toString();
        String medium = LocalDate.now().plusDays(45).toString();
        String farOut = LocalDate.now().plusDays(120).toString();
        ObjectNode urgent = objectMapper.createObjectNode()
                .put("id", "lic-urgent").put("licenseType", "Medical Practice").put("status", "ACTIVE").put("validTo", soon);
        ObjectNode high = objectMapper.createObjectNode()
                .put("id", "lic-high").put("licenseType", "Medical Practice").put("status", "ACTIVE").put("validTo", medium);
        ObjectNode ignored = objectMapper.createObjectNode()
                .put("id", "lic-far").put("licenseType", "Medical Practice").put("status", "ACTIVE").put("validTo", farOut);
        when(varapiClient.getProviderLicenses(PROVIDER_PUBLIC_ID))
                .thenReturn(objectMapper.createArrayNode().add(urgent).add(high).add(ignored));

        WorkHomeSection section = run();

        assertThat(section.items()).hasSize(2);
        Map<String, Object> urgentItem = section.items().stream()
                .filter(i -> i.get("id").equals("licence:lic-urgent")).findFirst().orElseThrow();
        Map<String, Object> highItem = section.items().stream()
                .filter(i -> i.get("id").equals("licence:lic-high")).findFirst().orElseThrow();
        assertThat(urgentItem.get("priority")).isEqualTo("URGENT");
        assertThat(highItem.get("priority")).isEqualTo("HIGH");
    }

    @Test
    void cpdRequirementMet_producesNoAlert() {
        stubProvider();
        ObjectNode cpd = objectMapper.createObjectNode()
                .put("id", "cpd-1").put("requiredPoints", 20).put("earnedPoints", 25).put("pointsRequirementMet", true);
        when(varapiClient.getProviderCpdSummary(PROVIDER_PUBLIC_ID)).thenReturn(cpd);

        WorkHomeSection section = run();

        assertThat(section.status()).isEqualTo("EMPTY");
    }

    @Test
    void cpdRequirementNotMet_producesOutstandingAlertWithPointsAndDate() {
        stubProvider();
        ObjectNode cpd = objectMapper.createObjectNode()
                .put("id", "cpd-1").put("requiredPoints", 20).put("earnedPoints", 5)
                .put("pointsRequirementMet", false).put("endDate", "2026-12-31");
        when(varapiClient.getProviderCpdSummary(PROVIDER_PUBLIC_ID)).thenReturn(cpd);

        WorkHomeSection section = run();

        assertThat(section.items()).hasSize(1);
        Map<String, Object> item = section.items().get(0);
        assertThat(item.get("kind")).isEqualTo("CPD_ALERT");
        assertThat(item.get("description")).isEqualTo("5 of 20 points earned — cycle ends 2026-12-31");
    }

    @Test
    void pendingAssignment_isLabelledPendingMyAcceptanceNotBarePending() {
        stubProvider();
        ObjectNode assignment = objectMapper.createObjectNode().put("id", "assign-1").put("roleDefinitionId", "role-99");
        when(governanceClient.searchAssignments("PROVIDER", PROVIDER_PUBLIC_ID, "PENDING"))
                .thenReturn(objectMapper.createArrayNode().add(assignment));

        WorkHomeSection section = run();

        Map<String, Object> item = section.items().get(0);
        assertThat(item.get("kind")).isEqualTo("ASSIGNMENT_ALERT");
        assertThat(item.get("status")).isEqualTo("PENDING_MY_ACCEPTANCE");
    }

    @Test
    void oneDownstreamThrows_stillReturnsAlertsFromTheOthers() {
        stubProvider();
        when(varapiClient.getProviderLicenses(PROVIDER_PUBLIC_ID)).thenThrow(new RuntimeException("varapi down"));
        ObjectNode cpd = objectMapper.createObjectNode()
                .put("requiredPoints", 20).put("earnedPoints", 5).put("pointsRequirementMet", false);
        when(varapiClient.getProviderCpdSummary(PROVIDER_PUBLIC_ID)).thenReturn(cpd);

        WorkHomeSection section = run();

        assertThat(section.status()).isEqualTo("OK");
        assertThat(section.items()).hasSize(1);
        assertThat(section.items().get(0).get("kind")).isEqualTo("CPD_ALERT");
    }
}
