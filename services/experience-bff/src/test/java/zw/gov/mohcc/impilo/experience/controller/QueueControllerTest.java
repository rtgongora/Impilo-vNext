package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueueControllerTest {

    @Test
    void listEntriesRequiresFacilityOrQueueScope() {
        QueueController controller = newController(false);

        var response = controller.listEntries(
                "tenant-1",
                "req-1",
                "corr-1",
                0,
                20,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void createEntryReturnsJourneyCorrelationMetaWhenPctSucceeds() throws Exception {
        PctServiceClient pctClient = mock(PctServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode journey = mapper.createObjectNode();
        journey.put("journeyId", "42");
        when(pctClient.startJourney(anyString(), any(UUID.class), any(), any())).thenReturn(journey);

        var queueArray = mapper.createArrayNode();
        var queueDef = queueArray.addObject();
        queueDef.put("queueType", "WALK_IN");
        queueDef.put("queueId", "11111111-1111-1111-1111-111111111111");
        when(pctClient.listQueues(any(UUID.class), any())).thenReturn(queueArray);

        ObjectNode item = mapper.createObjectNode();
        item.put("id", "22222222-2222-2222-2222-222222222222");
        when(pctClient.enqueue(any(UUID.class), anyString(), anyInt())).thenReturn(item);

        QueueController controller = newController(pctClient, false);

        var response = controller.createEntry(
                "tenant-1",
                "pod-1",
                "req-1",
                "corr-1",
                "idem-1",
                Map.of(
                        "patient_id", "a1000000-0000-0000-0000-000000000001",
                        "facility_id", "f1000000-0000-0000-0000-000000000001",
                        "queue_type", "WALK_IN",
                        "patient_cpid", "CPID-ZW-00001"
                )
        );

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) response.getBody().get("meta");
        assertEquals("42", meta.get("journey_id"));
        assertEquals("journey-42", meta.get("core_transaction_id"));
    }

    @Test
    void createEntryFailsClosedWhenPctUnavailable() {
        PctServiceClient pctClient = mock(PctServiceClient.class);
        when(pctClient.startJourney(anyString(), any(UUID.class), any(), any()))
                .thenThrow(new RuntimeException("pct down"));
        QueueController controller = newController(pctClient, false);

        var response = controller.createEntry(
                "tenant-1",
                "pod-1",
                "req-1",
                "corr-1",
                "idem-1",
                Map.of(
                        "patient_id", "a1000000-0000-0000-0000-000000000001",
                        "facility_id", "f1000000-0000-0000-0000-000000000001",
                        "queue_type", "WALK_IN"
                )
        );

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PCT_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void listQueueDefinitionsFailsClosedWhenPctUnavailable() {
        PctServiceClient pctClient = mock(PctServiceClient.class);
        when(pctClient.listQueues(any(UUID.class), any()))
                .thenThrow(new RuntimeException("pct down"));
        QueueController controller = newController(pctClient, false);

        var response = controller.listQueueDefinitions("req-1", "corr-1", "f1000000-0000-0000-0000-000000000001");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PCT_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void statusUpdateNoLongerReturnsSyntheticSuccessWhenEntryMissing() {
        PctServiceClient pctClient = mock(PctServiceClient.class);
        when(pctClient.updateQueueItemStatus(any(UUID.class), anyString()))
                .thenThrow(new RuntimeException("pct down"));
        QueueController controller = newController(pctClient, true);

        var response = controller.callEntry(
                "11111111-1111-1111-1111-111111111111",
                "req-1",
                "corr-1",
                "idem-1"
        );

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("QUEUE_ENTRY_NOT_FOUND", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static QueueController newController(boolean allowLocalFallback) {
        return newController(mock(PctServiceClient.class), allowLocalFallback);
    }

    private static QueueController newController(PctServiceClient pctClient, boolean allowLocalFallback) {
        QueueController controller = new QueueController(pctClient, mock(TshepoAuditServiceClient.class));
        ReflectionTestUtils.setField(controller, "allowLocalFallback", allowLocalFallback);
        return controller;
    }
}
