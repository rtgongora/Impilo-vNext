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

class LabReconciliationControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listPendingReconciliations_mapsOrosPagedPayload() {
        OrosServiceClient orosClient = mock(OrosServiceClient.class);
        ObjectNode page = MAPPER.createObjectNode();
        ArrayNode items = MAPPER.createArrayNode();
        items.add(MAPPER.createObjectNode()
                .put("recId", "rec-1")
                .put("orderId", "ord-1")
                .put("externalKey", "LIS-ACC-99")
                .put("externalSystem", "LIMS_A")
                .put("patientCpid", "CPID-001")
                .put("status", "PENDING"));
        page.set("items", items);
        page.put("page", 0);
        page.put("size", 20);
        page.put("totalElements", 1);

        when(orosClient.getReconcilePending(eq(0), eq(20))).thenReturn(page);

        LabReconciliationController controller = new LabReconciliationController(orosClient);
        var response = controller.listPendingReconciliations(
                "tenant-1", "req-1", "corr-1", 0, 20);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mappedItems = (List<Map<String, Object>>) data.get("items");
        assertEquals(1, mappedItems.size());
        assertEquals("LIS-ACC-99", mappedItems.get(0).get("externalKey"));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) data.get("summary");
        assertEquals(1, summary.get("pending"));
    }

    @Test
    void matchReconciliation_delegatesToOros() {
        OrosServiceClient orosClient = mock(OrosServiceClient.class);
        ObjectNode matched = MAPPER.createObjectNode().put("recId", "rec-9").put("status", "MATCHED");
        when(orosClient.matchReconciliation("rec-9", "ord-42")).thenReturn(matched);

        LabReconciliationController controller = new LabReconciliationController(orosClient);
        var response = controller.matchReconciliation(
                "rec-9", "tenant-1", "pod-1", "req-2", "corr-2", "idem-1",
                new LabReconciliationController.MatchLabReconciliationRequest("ord-42"));

        assertEquals(200, response.getStatusCode().value());
        verify(orosClient).matchReconciliation("rec-9", "ord-42");
    }

    @Test
    void resolveReconciliation_delegatesToOrosWithNotes() {
        OrosServiceClient orosClient = mock(OrosServiceClient.class);
        ObjectNode resolved = MAPPER.createObjectNode().put("recId", "rec-3").put("status", "RESOLVED");
        when(orosClient.resolveReconciliation("rec-3", "Manual override after LIS outage")).thenReturn(resolved);

        LabReconciliationController controller = new LabReconciliationController(orosClient);
        var response = controller.resolveReconciliation(
                "rec-3", "tenant-1", "pod-1", "req-3", "corr-3", "idem-2",
                new LabReconciliationController.ResolveLabReconciliationRequest("Manual override after LIS outage"));

        assertEquals(200, response.getStatusCode().value());
        verify(orosClient).resolveReconciliation("rec-3", "Manual override after LIS outage");
    }

    @Test
    void normalizeReconciliationPayload_handlesArrayFallback() {
        ArrayNode items = MAPPER.createArrayNode();
        items.add(MAPPER.createObjectNode()
                .put("recId", "rec-2")
                .put("status", "MATCHED"));

        Map<String, Object> payload = LabReconciliationController.normalizeReconciliationPayload(items, 0, 10);
        assertNotNull(payload.get("items"));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) payload.get("summary");
        assertEquals(1, summary.get("matched"));
    }
}
