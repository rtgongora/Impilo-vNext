package zw.gov.mohcc.impilo.experience.telemedicine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Registry resolution order: TUSO → cached-last-good → classpath fallback,
 * with honest source + registryDegraded provenance at each step.
 */
@ExtendWith(MockitoExtension.class)
class TelemedicineOperatingModelRegistryTest {

    @Mock private TusoServiceClient tusoServiceClient;

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode tusoRows() {
        return mapper.valueToTree(List.of(Map.of(
                "vsUid", "vh-provincial-zw-ha",
                "name", "Harare Provincial Virtual Hospital",
                "level", "PROVINCIAL",
                "operatingModel", "NETWORKED",
                "lifecycleStatus", "ACTIVATION_REQUESTED",
                "provinceCode", "ZW-HA",
                "serviceLines", List.of("Provincial general teleconsult"),
                "routingSeams", List.of(Map.of(
                        "id", 1, "routingType", "SPECIALTY_POOL",
                        "targetRef", "PROVINCIAL_ZW_HA", "active", true)),
                "queueDefs", List.of(Map.of(
                        "queueKey", "provincial-general",
                        "name", "Provincial General Teleconsult Queue",
                        "sourceRef", "vh-provincial-zw-ha:provincial-general")))));
    }

    @Test
    void tusoRowsAreServedAsSourceTuso() {
        when(tusoServiceClient.listVirtualServices()).thenReturn(tusoRows());
        var registry = new TelemedicineOperatingModelRegistry(tusoServiceClient);

        var directory = registry.virtualHospitalDirectory();

        assertThat(directory.source()).isEqualTo(TelemedicineOperatingModelRegistry.SOURCE_TUSO);
        assertThat(directory.registryDegraded()).isFalse();
        assertThat(directory.virtualHospitals()).hasSize(1);
        Map<String, Object> vh = directory.virtualHospitals().get(0);
        assertThat(vh.get("id")).isEqualTo("vh-provincial-zw-ha");
        // Computed, never stored: active seam but not ACTIVE → routable, not operational.
        assertThat(vh.get("substrateStatus")).isEqualTo("ROUTABLE_VIA_EXISTING_SEAMS");
        assertThat(vh.get("lifecycleStatus")).isEqualTo("ACTIVATION_REQUESTED");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queues = (List<Map<String, Object>>) vh.get("queues");
        assertThat(queues.get(0).get("id")).isEqualTo("provincial-general");
        assertThat(queues.get(0).get("status")).isEqualTo("AWAITING_BACKEND");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> seams = (List<Map<String, Object>>) vh.get("currentRoutingSeams");
        assertThat(seams.get(0).get("targetRef")).isEqualTo("PROVINCIAL_ZW_HA");
    }

    @Test
    void tusoDownWithoutCacheFallsBackToStaticAndFlagsDegraded() {
        when(tusoServiceClient.listVirtualServices()).thenThrow(new ResourceAccessException("connection refused"));
        var registry = new TelemedicineOperatingModelRegistry(tusoServiceClient);

        var directory = registry.virtualHospitalDirectory();

        assertThat(directory.source()).isEqualTo(TelemedicineOperatingModelRegistry.SOURCE_STATIC_FALLBACK);
        assertThat(directory.registryDegraded()).isTrue();
        assertThat(directory.virtualHospitals()).hasSize(21);
    }

    @Test
    void tusoDownAfterSuccessServesCachedLastGoodFlaggedDegraded() {
        when(tusoServiceClient.listVirtualServices())
                .thenReturn(tusoRows())
                .thenThrow(new ResourceAccessException("connection refused"));
        var registry = new TelemedicineOperatingModelRegistry(tusoServiceClient);

        assertThat(registry.virtualHospitalDirectory().source())
                .isEqualTo(TelemedicineOperatingModelRegistry.SOURCE_TUSO);
        var degraded = registry.virtualHospitalDirectory();

        assertThat(degraded.source()).isEqualTo(TelemedicineOperatingModelRegistry.SOURCE_TUSO);
        assertThat(degraded.registryDegraded()).isTrue();
        assertThat(degraded.virtualHospitals()).hasSize(1);
    }

    @Test
    void tusoReachableButUnpopulatedServesStaticWithoutDegradedFlag() {
        when(tusoServiceClient.listVirtualServices()).thenReturn(mapper.createArrayNode());
        var registry = new TelemedicineOperatingModelRegistry(tusoServiceClient);

        var directory = registry.virtualHospitalDirectory();

        assertThat(directory.source()).isEqualTo(TelemedicineOperatingModelRegistry.SOURCE_STATIC_FALLBACK);
        assertThat(directory.registryDegraded()).isFalse();
        assertThat(directory.virtualHospitals()).hasSize(21);
    }

    @Test
    void activeLifecycleComputesOperationalSubstrateStatus() {
        var rows = mapper.valueToTree(List.of(Map.of(
                "vsUid", "vh-x", "name", "X", "level", "PROVINCIAL", "operatingModel", "NETWORKED",
                "lifecycleStatus", "ACTIVE",
                "routingSeams", List.of(Map.of("routingType", "SPECIALTY_POOL", "targetRef", "P", "active", true)),
                "queueDefs", List.of())));
        when(tusoServiceClient.listVirtualServices()).thenReturn(rows);
        var registry = new TelemedicineOperatingModelRegistry(tusoServiceClient);

        assertThat(registry.virtualHospitalDirectory().virtualHospitals().get(0).get("substrateStatus"))
                .isEqualTo("OPERATIONAL");
    }
}
