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

    // ── implant lifecycle (Wave P8 §14, wired by SB-3) ──

    @Test
    void removeImplant_wrapsRemovedStatus() {
        InventoryController controller = new InventoryController(new StubInventoryClient(), mapper);
        ResponseEntity<Map<String, Object>> r = controller.removeImplant("req-ri", "corr-ri",
                UUID.randomUUID(), mapper.createObjectNode().put("removalReason", "infection"));
        assertEquals(200, r.getStatusCode().value());
        assertEquals("REMOVED", ((JsonNode) r.getBody().get("data")).get("status").asText());
    }

    @Test
    void reviseImplant_wrapsNewUnit() {
        InventoryController controller = new InventoryController(new StubInventoryClient(), mapper);
        ResponseEntity<Map<String, Object>> r = controller.reviseImplant("req-rv", "corr-rv",
                UUID.randomUUID(), mapper.createObjectNode().put("revisionReason", "wear"));
        assertEquals(200, r.getStatusCode().value());
        assertEquals("unit-2", ((JsonNode) r.getBody().get("data")).get("implantUnitId").asText());
    }

    @Test
    void recallTrace_returnsRows() {
        InventoryController controller = new InventoryController(new StubInventoryClient(), mapper);
        ResponseEntity<Map<String, Object>> r =
                controller.traceImplantRecall("req-rc", "corr-rc", "UDI-1", null);
        assertEquals(200, r.getStatusCode().value());
        assertEquals(1, ((JsonNode) r.getBody().get("data")).size());
    }

    /**
     * The honesty guarantee this wave is delivered under: a failed recall trace returns 502
     * with an explicit error, never an empty list — an empty trace claims "no patient carries
     * this recalled device", which a failed lookup must never claim.
     */
    @Test
    void recallTraceFailure_is502NeverAnEmptyList() {
        InventoryController controller = new InventoryController(new StubInventoryClient(), mapper);
        ResponseEntity<Map<String, Object>> r =
                controller.traceImplantRecall("req-rf", "corr-rf", "UDI-BOOM", null);
        assertEquals(502, r.getStatusCode().value());
        assertEquals("implant_recall_trace_unavailable", r.getBody().get("error"));
    }

    /** Same for a failed removal write: 502 + "NOT been saved", not a fabricated success. */
    @Test
    void removeImplantFailure_is502() {
        InventoryController controller = new InventoryController(new StubInventoryClient(), mapper);
        ResponseEntity<Map<String, Object>> r = controller.removeImplant("req-rx", "corr-rx",
                UUID.fromString("00000000-0000-0000-0000-00000000b00b"),
                mapper.createObjectNode());
        assertEquals(502, r.getStatusCode().value());
        assertEquals("implant_removal_unavailable", r.getBody().get("error"));
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

        @Override public JsonNode removeImplant(String patientImplantId, JsonNode body) {
            if (patientImplantId.endsWith("b00b")) { throw new RuntimeException("inventory down"); }
            return mapper.createObjectNode().put("patientImplantId", patientImplantId).put("status", "REMOVED");
        }

        @Override public JsonNode reviseImplant(String patientImplantId, JsonNode body) {
            return mapper.createObjectNode().put("implantUnitId", "unit-2").put("status", "IMPLANTED");
        }

        @Override public JsonNode traceImplantRecall(String udi, String lot) {
            if ("UDI-BOOM".equals(udi)) { throw new RuntimeException("inventory down"); }
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("udi", udi).put("patientCpid", "CPID-1"));
            return arr;
        }
    }
}
