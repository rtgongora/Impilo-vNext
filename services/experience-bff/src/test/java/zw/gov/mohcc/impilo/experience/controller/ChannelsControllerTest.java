package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ChannelsServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChannelsControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void list_returnsDataAndMeta() {
        ChannelsController controller = new ChannelsController(new StubChannelsClient());
        ResponseEntity<Map<String, Object>> response =
                controller.list("req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void get_returnsChannelAndMeta() {
        ChannelsController controller = new ChannelsController(new StubChannelsClient());
        ResponseEntity<Map<String, Object>> response =
                controller.get("ch-1", "req-2", "corr-2");
        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void create_returns201() {
        ChannelsController controller = new ChannelsController(new StubChannelsClient());
        ResponseEntity<Map<String, Object>> response =
                controller.create("req-3", "corr-3", Map.of("name", "SMS", "type", "SMS"));
        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-3", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return new ServiceClientConfig.ServiceEndpoints(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        );
    }

    private static final class StubChannelsClient extends ChannelsServiceClient {
        StubChannelsClient() { super(new RestTemplate(), endpoints()); }

        @Override public JsonNode listChannels() {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "ch-1").put("type", "SMS"));
            return arr;
        }

        @Override public JsonNode getChannel(String id) {
            return mapper.createObjectNode().put("id", id).put("type", "SMS");
        }

        @Override public JsonNode createChannel(Map<String, Object> body) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", "ch-new");
            node.put("name", body.get("name").toString());
            return node;
        }
    }
}
