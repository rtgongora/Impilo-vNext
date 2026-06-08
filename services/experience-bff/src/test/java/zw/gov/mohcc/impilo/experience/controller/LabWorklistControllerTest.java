package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LabWorklistControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listLabWorklists_mapsOrosPagedPayload() {
        OrosServiceClient orosClient = mock(OrosServiceClient.class);
        ObjectNode page = MAPPER.createObjectNode();
        ArrayNode items = MAPPER.createArrayNode();
        items.add(MAPPER.createObjectNode()
                .put("orderId", "ord-1")
                .put("orderType", "LAB")
                .put("status", "PLACED")
                .put("priority", "STAT")
                .put("patientCpid", "CPID-001"));
        page.set("items", items);
        page.put("page", 0);
        page.put("size", 20);
        page.put("totalElements", 1);

        when(orosClient.getWorklists(eq("fac-1"), isNull(), eq("LAB"), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(page);

        LabWorklistController controller = new LabWorklistController(orosClient);
        var response = controller.listLabWorklists(
                "tenant-1", "req-1", "corr-1", "fac-1", null, "LAB", null, null, 0, 20);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mappedItems = (List<Map<String, Object>>) data.get("items");
        assertEquals(1, mappedItems.size());
        assertEquals("ord-1", mappedItems.get(0).get("orderId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) data.get("summary");
        assertEquals(1, summary.get("pending_collection"));
        assertEquals(1, summary.get("urgent"));
    }

    @Test
    void acceptLabWorklistItem_delegatesToOros() {
        OrosServiceClient orosClient = mock(OrosServiceClient.class);
        ObjectNode accepted = MAPPER.createObjectNode().put("orderId", "ord-9").put("status", "ACCEPTED");
        when(orosClient.acceptWorklistItem("ord-9")).thenReturn(accepted);

        LabWorklistController controller = new LabWorklistController(orosClient);
        var response = controller.acceptLabWorklistItem(
                "ord-9", "tenant-1", "pod-1", "req-2", "corr-2", "idem-1");

        assertEquals(200, response.getStatusCode().value());
        verify(orosClient).acceptWorklistItem("ord-9");
    }

    @Test
    void rejectLabWorklistItem_delegatesToOrosWithReason() {
        OrosServiceClient orosClient = mock(OrosServiceClient.class);
        ObjectNode rejected = MAPPER.createObjectNode().put("orderId", "ord-3").put("status", "REJECTED");
        when(orosClient.rejectWorklistItem("ord-3", "Specimen unusable")).thenReturn(rejected);

        LabWorklistController controller = new LabWorklistController(orosClient);
        var response = controller.rejectLabWorklistItem(
                "ord-3", "tenant-1", "pod-1", "req-3", "corr-3", "idem-2",
                new LabWorklistController.RejectLabWorklistRequest("Specimen unusable"));

        assertEquals(200, response.getStatusCode().value());
        verify(orosClient).rejectWorklistItem("ord-3", "Specimen unusable");
    }

    @Test
    void normalizeWorklistPayload_handlesArrayFallback() {
        ArrayNode items = MAPPER.createArrayNode();
        items.add(MAPPER.createObjectNode()
                .put("orderId", "ord-2")
                .put("status", "IN_PROGRESS")
                .put("priority", "ROUTINE"));

        Map<String, Object> payload = LabWorklistController.normalizeWorklistPayload(items, 0, 10);
        assertNotNull(payload.get("items"));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) payload.get("summary");
        assertEquals(1, summary.get("in_progress"));
    }
}
