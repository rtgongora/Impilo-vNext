package zw.gov.mohcc.impilo.experience.worklist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalWorklistComposerTest {

    private static final String FACILITY_ID = UUID.randomUUID().toString();

    @Mock
    private PctServiceClient pctClient;
    @Mock
    private OrosServiceClient orosClient;
    @Mock
    private PharmacyServiceClient pharmacyClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ClinicalWorklistComposer composer;

    @BeforeEach
    void setUp() {
        composer = new ClinicalWorklistComposer(pctClient, orosClient, pharmacyClient, objectMapper);
        lenient().when(pctClient.listQueues(any(), any())).thenReturn(objectMapper.createArrayNode());
        lenient().when(pctClient.listIncomingReferrals(anyString(), any(), anyInt(), anyInt())).thenReturn(objectMapper.createArrayNode());
        lenient().when(pctClient.listWorkspaceTasks(any())).thenReturn(objectMapper.createArrayNode());
        lenient().when(orosClient.getWorklist(anyString(), any())).thenReturn(objectMapper.createArrayNode());
        lenient().when(pharmacyClient.getWorklist(anyString(), any())).thenReturn(objectMapper.createArrayNode());
        lenient().when(pctClient.listTelehealthSessions(anyString(), any(), anyInt(), anyInt())).thenReturn(objectMapper.createArrayNode());
    }

    @Test
    void appendQueueItems_skipsEmptyQueuesAndRanksByWaitingCount() {
        JsonNode queues = objectMapper.createArrayNode()
                .add(queue("OPD", 20))
                .add(queue("EYE", 0))
                .add(queue("ANC", 5));
        when(pctClient.listQueues(any(), any())).thenReturn(queues);

        List<Map<String, Object>> out = new ArrayList<>();
        composer.appendQueueItems(out, FACILITY_ID, 40);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).get("priority")).isEqualTo("URGENT");
        assertThat(out.get(1).get("priority")).isEqualTo("MEDIUM");
    }

    @Test
    void appendReferralItems_mapsFieldsAndDefaultsPriorityAndStatus() {
        JsonNode referrals = objectMapper.createArrayNode().add(
                objectMapper.createObjectNode()
                        .put("id", "ref-1")
                        .put("specialty", "Cardiology")
                        .put("patientId", "cpid-1")
        );
        when(pctClient.listIncomingReferrals(anyString(), any(), anyInt(), anyInt())).thenReturn(referrals);

        List<Map<String, Object>> out = new ArrayList<>();
        composer.appendReferralItems(out, FACILITY_ID, 40);

        assertThat(out).hasSize(1);
        Map<String, Object> item = out.get(0);
        assertThat(item.get("id")).isEqualTo("referral:ref-1");
        assertThat(item.get("kind")).isEqualTo("REFERRAL");
        assertThat(item.get("title")).isEqualTo("Cardiology");
        assertThat(item.get("priority")).isEqualTo("MEDIUM");
        assertThat(item.get("status")).isEqualTo("PENDING");
        assertThat(item.get("patient_id")).isEqualTo("cpid-1");
    }

    @Test
    void appendTelemedicineItems_filtersOutCompletedAndCancelledSessions() {
        JsonNode sessions = objectMapper.createArrayNode()
                .add(session("s1", "SCHEDULED"))
                .add(session("s2", "COMPLETED"))
                .add(session("s3", "CANCELLED"))
                .add(session("s4", "WAITING"));
        when(pctClient.listTelehealthSessions(anyString(), any(), anyInt(), anyInt())).thenReturn(sessions);

        List<Map<String, Object>> out = new ArrayList<>();
        composer.appendTelemedicineItems(out, FACILITY_ID, 40);

        assertThat(out).hasSize(2);
        assertThat(out).allSatisfy(item -> assertThat(item.get("kind")).isEqualTo("TELEMEDICINE"));
    }

    @Test
    void appendX_downstreamThrows_failsClosedToEmptyRatherThanPropagating() {
        when(orosClient.getWorklist(anyString(), any())).thenThrow(new RuntimeException("oros down"));

        List<Map<String, Object>> out = new ArrayList<>();
        composer.appendOrderItems(out, FACILITY_ID, 40);

        assertThat(out).isEmpty();
    }

    @Test
    void composeAll_stopsAtSizeCapAcrossFamilies() {
        JsonNode queues = objectMapper.createArrayNode().add(queue("OPD", 20)).add(queue("EYE", 12));
        when(pctClient.listQueues(any(), any())).thenReturn(queues);
        JsonNode referrals = objectMapper.createArrayNode().add(referral("r1")).add(referral("r2"));
        when(pctClient.listIncomingReferrals(anyString(), any(), anyInt(), anyInt())).thenReturn(referrals);

        List<Map<String, Object>> out = composer.composeAll(FACILITY_ID, 3);

        assertThat(out).hasSize(3);
    }

    private static JsonNode queue(String type, int waiting) {
        ObjectMapper m = new ObjectMapper();
        return m.createObjectNode().put("queueType", type).put("waitingCount", waiting);
    }

    private static JsonNode referral(String id) {
        ObjectMapper m = new ObjectMapper();
        return m.createObjectNode().put("id", id);
    }

    private static JsonNode session(String id, String status) {
        ObjectMapper m = new ObjectMapper();
        return m.createObjectNode().put("id", id).put("status", status);
    }
}
