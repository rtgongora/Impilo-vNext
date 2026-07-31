package zw.gov.mohcc.impilo.experience.workhome;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.TusoFacilityClaimClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityManagementWorkHomeAdapterTest {

    private static final String FACILITY_UUID = "facility-uuid-1";

    @Mock
    private TusoServiceClient tusoClient;
    @Mock
    private TusoFacilityClaimClient facilityClaimClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FacilityManagementWorkHomeAdapter adapter;

    private ResolvedWorkContext context() {
        return new ResolvedWorkContext(
                "ctx-1", "facility", "TUSO", "rec-1",
                FACILITY_UUID, null, null, null,
                "FACILITY_ADMINISTRATOR", List.of(), List.of("FACILITY_MANAGEMENT"), "FACILITY_MANAGEMENT", "CATALOG",
                null, null, null, null, null,
                null, List.of(), "Test facility", "regular", 300);
    }

    @BeforeEach
    void setUp() {
        adapter = new FacilityManagementWorkHomeAdapter(tusoClient, facilityClaimClient);
        lenient().when(tusoClient.getFacilityStatusComposite(FACILITY_UUID)).thenReturn(objectMapper.nullNode());
        lenient().when(facilityClaimClient.appointments(FACILITY_UUID)).thenReturn(objectMapper.createArrayNode());
    }

    @Test
    void family_isFacilityManagement() {
        assertThat(adapter.family()).isEqualTo(WorkHomeFamily.FACILITY_MANAGEMENT);
    }

    @Test
    void declaresTwoSections_statusAndAdminClaims() {
        List<FanoutTask<WorkHomeSection>> tasks = adapter.sectionTasks(context());
        assertThat(tasks).extracting(FanoutTask::sectionId)
                .containsExactlyInAnyOrder("facility-status", "facility-admin-claims");
    }

    @Test
    void adminClaims_onlyIncludesPendingAppointmentsAndLabelsThemAwaitingMyApproval() {
        ObjectNode pending = objectMapper.createObjectNode()
                .put("id", "app-1").put("approvalState", "PENDING").put("role", "FACILITY_ADMINISTRATOR");
        ObjectNode active = objectMapper.createObjectNode()
                .put("id", "app-2").put("approvalState", "ACTIVE").put("role", "FACILITY_ADMINISTRATOR");
        when(facilityClaimClient.appointments(FACILITY_UUID))
                .thenReturn(objectMapper.createArrayNode().add(pending).add(active));

        WorkHomeSection section = sectionTask(adapter.sectionTasks(context()), "facility-admin-claims");

        assertThat(section.status()).isEqualTo("OK");
        assertThat(section.items()).hasSize(1);
        Map<String, Object> item = section.items().get(0);
        assertThat(item.get("id")).isEqualTo("admin-claim:app-1");
        // Deliberately not "PENDING": see WorkHomeBucketing — bare PENDING is read as
        // "awaiting the viewer", the opposite of what an admin claim awaiting approval means.
        assertThat(item.get("status")).isEqualTo("PENDING_MY_APPROVAL");
        assertThat(item.get("priority")).isEqualTo("HIGH");
        assertThat(item.get("href")).isEqualTo("/facility/" + FACILITY_UUID + "/mode");
    }

    @Test
    void facilityStatus_hrefTargetsFacilityModeDashboard() {
        ObjectNode composite = objectMapper.createObjectNode()
                .put("operatingStatus", "OPEN")
                .put("legitimacyStatus", "VERIFIED");
        when(tusoClient.getFacilityStatusComposite(FACILITY_UUID)).thenReturn(composite);

        WorkHomeSection section = sectionTask(adapter.sectionTasks(context()), "facility-status");

        assertThat(section.status()).isEqualTo("OK");
        assertThat(section.items()).hasSize(1);
        assertThat(section.items().get(0).get("href")).isEqualTo("/facility/" + FACILITY_UUID + "/mode");
    }

    @Test
    void adminClaims_noPendingAppointments_isEmptyNotDegraded() {
        WorkHomeSection section = sectionTask(adapter.sectionTasks(context()), "facility-admin-claims");
        assertThat(section.status()).isEqualTo("EMPTY");
    }

    @Test
    void facilityStatus_downstreamThrows_degradesThatSectionOnly() {
        when(tusoClient.getFacilityStatusComposite(FACILITY_UUID)).thenThrow(new RuntimeException("tuso down"));

        WorkHomeSection status = sectionTask(adapter.sectionTasks(context()), "facility-status");
        WorkHomeSection claims = sectionTask(adapter.sectionTasks(context()), "facility-admin-claims");

        assertThat(status.status()).isEqualTo("DEGRADED");
        assertThat(claims.status()).isEqualTo("EMPTY");
    }

    @Test
    void noFacilityAnchor_bothSectionsAreEmptyWithoutCallingDownstream() {
        ResolvedWorkContext noFacility = new ResolvedWorkContext(
                "ctx-1", "facility", "TUSO", "rec-1",
                null, null, null, null,
                "FACILITY_ADMINISTRATOR", List.of(), List.of("FACILITY_MANAGEMENT"), "FACILITY_MANAGEMENT", "CATALOG",
                null, null, null, null, null,
                null, List.of(), "Test facility", "regular", 300);

        WorkHomeSection status = sectionTask(adapter.sectionTasks(noFacility), "facility-status");
        WorkHomeSection claims = sectionTask(adapter.sectionTasks(noFacility), "facility-admin-claims");

        assertThat(status.status()).isEqualTo("EMPTY");
        assertThat(claims.status()).isEqualTo("EMPTY");
    }

    private static WorkHomeSection sectionTask(List<FanoutTask<WorkHomeSection>> tasks, String sectionId) {
        return tasks.stream().filter(t -> t.sectionId().equals(sectionId)).findFirst().orElseThrow().supplier().get();
    }
}
