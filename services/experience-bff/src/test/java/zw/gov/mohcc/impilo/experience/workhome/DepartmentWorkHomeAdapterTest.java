package zw.gov.mohcc.impilo.experience.workhome;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentWorkHomeAdapterTest {

    @Mock
    private TusoServiceClient tusoClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DepartmentWorkHomeAdapter adapter;

    private ResolvedWorkContext contextFor(String facilityUuid, String departmentId) {
        return new ResolvedWorkContext(
                "ctx-1", "facility", "TUSO", "rec-1",
                facilityUuid, null, null, null,
                "DEPARTMENT_HEAD", List.of(), List.of("DEPARTMENT_MANAGEMENT"), "DEPARTMENT_MANAGEMENT", "CATALOG",
                departmentId, null, null, null, null,
                null, List.of(), "Test department", "regular", 300);
    }

    @Test
    void family_isDepartmentManagement() {
        adapter = new DepartmentWorkHomeAdapter(tusoClient);
        assertThat(adapter.family()).isEqualTo(WorkHomeFamily.DEPARTMENT_MANAGEMENT);
    }

    @Test
    void noFacilityAnchor_returnsEmptySectionWithoutCallingDownstream() {
        adapter = new DepartmentWorkHomeAdapter(tusoClient);
        ResolvedWorkContext ctx = contextFor(null, "dept-1");

        WorkHomeSection section = adapter.sectionTasks(ctx).get(0).supplier().get();

        assertThat(section.status()).isEqualTo("EMPTY");
    }

    @Test
    void facilityNotFoundByUid_returnsEmptySectionNotDegraded() {
        adapter = new DepartmentWorkHomeAdapter(tusoClient);
        when(tusoClient.getFacilityByUid("facility-uuid-1")).thenReturn(objectMapper.nullNode());

        WorkHomeSection section = adapter.sectionTasks(contextFor("facility-uuid-1", "dept-1")).get(0).supplier().get();

        assertThat(section.status()).isEqualTo("EMPTY");
        assertThat(section.note()).contains("Facility not found");
    }

    @Test
    void matchingDepartmentFound_returnsOneItemBuiltFromRealFields() {
        adapter = new DepartmentWorkHomeAdapter(tusoClient);
        ObjectNode facility = objectMapper.createObjectNode().put("id", "42");
        when(tusoClient.getFacilityByUid("facility-uuid-1")).thenReturn(facility);

        ObjectNode dept1 = objectMapper.createObjectNode()
                .put("id", "dept-1").put("displayName", "Oncology Ward B")
                .put("operatingStatus", "OPEN").put("departmentType", "WARD");
        ObjectNode dept2 = objectMapper.createObjectNode().put("id", "dept-2").put("displayName", "Pharmacy");
        when(tusoClient.listFacilityDepartments(42L)).thenReturn(objectMapper.createArrayNode().add(dept1).add(dept2));

        WorkHomeSection section = adapter.sectionTasks(contextFor("facility-uuid-1", "dept-1")).get(0).supplier().get();

        assertThat(section.status()).isEqualTo("OK");
        assertThat(section.items()).hasSize(1);
        Map<String, Object> item = section.items().get(0);
        assertThat(item.get("id")).isEqualTo("department:dept-1");
        assertThat(item.get("title")).isEqualTo("Oncology Ward B");
        assertThat(item.get("status")).isEqualTo("OPEN");
        assertThat(item.get("href")).isEqualTo("/facility/facility-uuid-1/departments");
    }

    @Test
    void departmentNotAmongFacilitysDepartments_returnsEmptySection() {
        adapter = new DepartmentWorkHomeAdapter(tusoClient);
        ObjectNode facility = objectMapper.createObjectNode().put("id", "42");
        when(tusoClient.getFacilityByUid("facility-uuid-1")).thenReturn(facility);
        ObjectNode dept = objectMapper.createObjectNode().put("id", "dept-999").put("displayName", "Somewhere else");
        when(tusoClient.listFacilityDepartments(42L)).thenReturn(objectMapper.createArrayNode().add(dept));

        WorkHomeSection section = adapter.sectionTasks(contextFor("facility-uuid-1", "dept-1")).get(0).supplier().get();

        assertThat(section.status()).isEqualTo("EMPTY");
    }

    @Test
    void downstreamThrows_degradesInsteadOfPropagating() {
        adapter = new DepartmentWorkHomeAdapter(tusoClient);
        when(tusoClient.getFacilityByUid("facility-uuid-1")).thenThrow(new RuntimeException("tuso unreachable"));

        WorkHomeSection section = adapter.sectionTasks(contextFor("facility-uuid-1", "dept-1")).get(0).supplier().get();

        assertThat(section.status()).isEqualTo("DEGRADED");
        assertThat(section.note()).contains("tuso unreachable");
    }
}
