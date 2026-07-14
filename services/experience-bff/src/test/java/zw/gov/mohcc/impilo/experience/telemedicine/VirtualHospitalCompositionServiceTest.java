package zw.gov.mohcc.impilo.experience.telemedicine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * BFF composition truth: LIVE is only asserted when PCT confirms a
 * materialised+active queue; vashandi down → duty UNKNOWN, never an error.
 */
@ExtendWith(MockitoExtension.class)
class VirtualHospitalCompositionServiceTest {

    @Mock private PctServiceClient pctServiceClient;
    @Mock private VashandiServiceClient vashandiServiceClient;

    private final ObjectMapper mapper = new ObjectMapper();

    private VirtualHospitalCompositionService service() {
        return new VirtualHospitalCompositionService(pctServiceClient,
                new VirtualPoolDutyService(vashandiServiceClient));
    }

    private Map<String, Object> vh() {
        return Map.of(
                "id", "vh-provincial-zw-ha",
                "currentRoutingSeams", List.of(Map.of(
                        "routingType", "SPECIALTY_POOL", "targetRef", "PROVINCIAL_ZW_HA")),
                "queues", List.of(
                        Map.of("id", "provincial-general", "name", "Provincial General", "status", "AWAITING_BACKEND"),
                        Map.of("id", "district-escalation", "name", "District Escalation", "status", "AWAITING_BACKEND")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void materialisedActiveQueueComposesLiveWithDepthAndSla() {
        when(pctServiceClient.getVirtualPoolStats("PROVINCIAL_ZW_HA")).thenReturn(mapper.valueToTree(List.of(Map.of(
                "queueKey", "provincial-general", "active", true, "materializationStatus", "MATERIALIZED",
                "depth", 3, "oldestWaitingMinutes", 12, "slaBreaches", 1, "slaMinutes", 30))));
        when(vashandiServiceClient.getVirtualPoolOnDuty("PROVINCIAL_ZW_HA")).thenReturn(
                mapper.valueToTree(Map.of("poolId", "PROVINCIAL_ZW_HA", "onDutyCount", 2,
                        "onDuty", List.of(), "nextShiftStart", "2026-07-15T08:00:00Z")));

        Map<String, Object> composed = service().compose(vh());

        List<Map<String, Object>> queues = (List<Map<String, Object>>) composed.get("queues");
        Map<String, Object> live = queues.get(0);
        assertThat(live.get("status")).isEqualTo("LIVE");
        assertThat(live.get("depth")).isEqualTo(3L);
        assertThat(live.get("oldestWaitingMinutes")).isEqualTo(12L);
        assertThat(live.get("slaBreaches")).isEqualTo(1L);
        // Not materialised → stays honest AWAITING_BACKEND.
        assertThat(queues.get(1).get("status")).isEqualTo("AWAITING_BACKEND");

        Map<String, Object> duty = (Map<String, Object>) composed.get("duty");
        assertThat(duty.get("status")).isEqualTo("true");
        assertThat(duty.get("onDutyCount")).isEqualTo(2);
        assertThat(composed.get("queueStatsDegraded")).isEqualTo(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void vashandiDownYieldsDutyUnknownNeverAnError() {
        when(pctServiceClient.getVirtualPoolStats("PROVINCIAL_ZW_HA")).thenReturn(mapper.createArrayNode());
        when(vashandiServiceClient.getVirtualPoolOnDuty("PROVINCIAL_ZW_HA")).thenReturn(null);

        Map<String, Object> composed = service().compose(vh());

        Map<String, Object> duty = (Map<String, Object>) composed.get("duty");
        assertThat(duty.get("status")).isEqualTo("UNKNOWN");
        assertThat(duty).doesNotContainKey("onDutyCount");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pctDownKeepsQueuesAwaitingBackendAndFlagsDegraded() {
        when(pctServiceClient.getVirtualPoolStats("PROVINCIAL_ZW_HA"))
                .thenThrow(new ResourceAccessException("connection refused"));
        when(vashandiServiceClient.getVirtualPoolOnDuty("PROVINCIAL_ZW_HA")).thenReturn(
                mapper.valueToTree(Map.of("onDutyCount", 0, "onDuty", List.of())));

        Map<String, Object> composed = service().compose(vh());

        List<Map<String, Object>> queues = (List<Map<String, Object>>) composed.get("queues");
        assertThat(queues).allSatisfy(q -> assertThat(q.get("status")).isEqualTo("AWAITING_BACKEND"));
        assertThat(composed.get("queueStatsDegraded")).isEqualTo(true);
        Map<String, Object> duty = (Map<String, Object>) composed.get("duty");
        assertThat(duty.get("status")).isEqualTo("false"); // rostered nobody — honest false, not UNKNOWN
    }

    @Test
    void noActiveSeamComposesNothing() {
        Map<String, Object> composed = service().compose(Map.of("id", "vh-x", "currentRoutingSeams", List.of()));

        assertThat(composed).doesNotContainKeys("duty", "primaryPoolId", "queueStatsDegraded");
    }
}
