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

class LabCatalogControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listLabCatalog_mapsOrosPagedPayload() {
        OrosServiceClient orosClient = mock(OrosServiceClient.class);
        ObjectNode page = MAPPER.createObjectNode();
        ArrayNode items = MAPPER.createArrayNode();
        items.add(MAPPER.createObjectNode()
                .put("catalogId", "cat-1")
                .put("orderType", "LAB")
                .put("code", "FBC")
                .put("displayName", "Full Blood Count")
                .put("category", "Haematology")
                .put("active", true));
        page.set("items", items);
        page.put("page", 0);
        page.put("size", 20);
        page.put("totalElements", 1);

        when(orosClient.getCatalog(eq("LAB"), eq(0), eq(20))).thenReturn(page);

        LabCatalogController controller = new LabCatalogController(orosClient);
        var response = controller.listLabCatalog(
                "tenant-1", "req-1", "corr-1", "LAB", 0, 20);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mappedItems = (List<Map<String, Object>>) data.get("items");
        assertEquals(1, mappedItems.size());
        assertEquals("FBC", mappedItems.get(0).get("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) data.get("summary");
        assertEquals(1, summary.get("active"));
        verify(orosClient).getCatalog("LAB", 0, 20);
    }

    @Test
    void normalizeCatalogPayload_handlesArrayFallback() {
        ArrayNode items = MAPPER.createArrayNode();
        items.add(MAPPER.createObjectNode()
                .put("code", "U&E")
                .put("category", "Chemistry")
                .put("active", true));

        Map<String, Object> payload = LabCatalogController.normalizeCatalogPayload(items, 0, 10);
        assertNotNull(payload.get("items"));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) payload.get("summary");
        assertEquals(1, summary.get("total"));
    }
}
