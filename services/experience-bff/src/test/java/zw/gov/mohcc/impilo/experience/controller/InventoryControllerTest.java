package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.InventoryServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InventoryControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listItems_returnsDataAndMeta() {
        InventoryController controller = new InventoryController(new StubInventoryClient(), mapper);
        UUID facilityId = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> response =
                controller.listItems("t1", "req-1", "corr-1", 0, 20, facilityId.toString());
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listMovements_returnsDataAndMeta() {
        InventoryController controller = new InventoryController(new StubInventoryClient(), mapper);
        UUID facilityId = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> response =
                controller.listMovements("t1", "req-2", "corr-2", facilityId.toString());
        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void createRequisition_returnsDataAndMeta() {
        InventoryController controller = new InventoryController(new StubInventoryClient(), mapper);
        InventoryController.CreateRequisitionRequest request =
                new InventoryController.CreateRequisitionRequest(
                        UUID.randomUUID().toString(), "REQ-001", "user-1", 5, "2026-04-20", "Urgent");
        ResponseEntity<Map<String, Object>> response =
                controller.createRequisition("t1", "req-3", "corr-3", request);
        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-3", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubInventoryClient extends InventoryServiceClient {
        StubInventoryClient() { super(new RestTemplate(), endpoints()); }

        @Override public JsonNode getOnHand(UUID facilityId, UUID storeId, UUID binId,
                                            String itemCode, int page, int size) {
            ArrayNode arr = mapper.createArrayNode();
            ObjectNode item = mapper.createObjectNode();
            item.put("itemCode", "MED-001");
            item.put("quantity", 100);
            arr.add(item);
            return arr;
        }

        @Override public JsonNode getLedger(UUID facilityId, UUID storeId,
                                            String itemCode, int page, int size) {
            return mapper.createArrayNode();
        }

        @Override public JsonNode getReconcilePending(int page, int size) {
            return mapper.createArrayNode();
        }

        @Override public JsonNode createRequisition(JsonNode body) {
            ObjectNode node = mapper.createObjectNode();
            node.put("reqId", UUID.randomUUID().toString());
            node.put("status", "DRAFT");
            node.put("requestedBy", "user-1");
            return node;
        }

        @Override public JsonNode listRequisitions(UUID facilityId, int page, int size) {
            return mapper.createArrayNode();
        }
    }
}
