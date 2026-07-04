package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueueControllerTest {

    @Test
    void listEntriesRequiresFacilityOrQueueScope() {
        QueueController controller = newController();

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

        QueueController controller = newController(pctClient);

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
        QueueController controller = newController(pctClient);

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
        QueueController controller = newController(pctClient);

        var response = controller.listQueueDefinitions("req-1", "corr-1", "f1000000-0000-0000-0000-000000000001");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PCT_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void triageEntryForwardsInTriageStatusToPct() {
        // Regression: BFF sends the literal IN_TRIAGE status; PCT's QueueItemStatus
        // must accept it (this transition used to 500 on the PCT side).
        PctServiceClient pctClient = mock(PctServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode item = mapper.createObjectNode();
        item.put("status", "IN_TRIAGE");
        when(pctClient.updateQueueItemStatus(any(UUID.class), eq("IN_TRIAGE"))).thenReturn(item);
        QueueController controller = newController(pctClient);

        var response = controller.triageEntry(
                "11111111-1111-1111-1111-111111111111",
                "req-1",
                "corr-1",
                "idem-1",
                Map.of()
        );

        assertEquals(200, response.getStatusCode().value());
        verify(pctClient).updateQueueItemStatus(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "IN_TRIAGE");
    }

    @Test
    void createEntryMapsSymbolicPriorityToPctTriageScale() throws Exception {
        // EMERGENCY must land on PCT's 1–5 scale (5 = most urgent), not the old 100.
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

        QueueController controller = newController(pctClient);

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
                        "priority", "EMERGENCY"
                )
        );

        assertEquals(201, response.getStatusCode().value());
        ArgumentCaptor<Integer> priorityCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(pctClient).enqueue(any(UUID.class), anyString(), priorityCaptor.capture());
        assertEquals(5, priorityCaptor.getValue());
    }

    @Test
    void statusUpdateFailsCleanWhenPctUnavailable_neverFabricatesLocalSuccess() {
        PctServiceClient pctClient = mock(PctServiceClient.class);
        when(pctClient.updateQueueItemStatus(any(UUID.class), anyString()))
                .thenThrow(new RuntimeException("pct down"));
        QueueController controller = newController(pctClient);

        var response = controller.callEntry(
                "11111111-1111-1111-1111-111111111111",
                "req-1",
                "corr-1",
                "idem-1"
        );

        // No in-memory queue exists any more: a status update against an unreachable
        // pct-service fails clean (502) rather than mutating/serving a fabricated entry.
        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PCT_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static QueueController newController() {
        return newController(mock(PctServiceClient.class));
    }

    private static QueueController newController(PctServiceClient pctClient) {
        return new QueueController(pctClient, mock(TshepoAuditServiceClient.class));
    }
}
