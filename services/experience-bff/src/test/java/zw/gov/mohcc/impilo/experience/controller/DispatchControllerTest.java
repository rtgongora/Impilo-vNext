package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.DispatchServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DispatchControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listTasks_returnsDataAndMeta() {
        DispatchController controller = new DispatchController(new StubDispatchClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listTasks(null, null, "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void createTask_returns201() {
        DispatchController controller = new DispatchController(new StubDispatchClient());
        ResponseEntity<Map<String, Object>> response =
                controller.createTask("req-2", "corr-2",
                        Map.of("type", "DELIVERY", "description", "Deliver supplies"));
        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void getTask_returnsTaskData() {
        DispatchController controller = new DispatchController(new StubDispatchClient());
        ResponseEntity<Map<String, Object>> response =
                controller.getTask("task-1", "req-3", "corr-3");
        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-3", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listDeliveries_returnsDataAndMeta() {
        DispatchController controller = new DispatchController(new StubDispatchClient());
        ResponseEntity<Map<String, Object>> response = controller.listDeliveries("req-dlv", "corr-dlv");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-dlv", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubDispatchClient extends DispatchServiceClient {
        StubDispatchClient() { super(new RestTemplate(), endpoints()); }

        @Override public JsonNode listTasks(String assigneeId, String status) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "task-1").put("status", "PENDING"));
            return arr;
        }

        @Override public JsonNode getTask(String id) {
            return mapper.createObjectNode().put("id", id).put("status", "PENDING");
        }

        @Override public JsonNode createTask(Map<String, Object> body) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", "task-new");
            node.put("type", body.get("type").toString());
            return node;
        }

        @Override public JsonNode assignTask(String id, Map<String, Object> body) {
            return mapper.createObjectNode().put("id", id).put("status", "ASSIGNED");
        }

        @Override public JsonNode completeTask(String id, Map<String, Object> body) {
            return mapper.createObjectNode().put("id", id).put("status", "COMPLETED");
        }

        @Override public JsonNode listDeliveries() {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "delivery-1").put("status", "IN_TRANSIT"));
            ObjectNode root = mapper.createObjectNode();
            root.set("items", arr);
            return root;
        }
    }
}
