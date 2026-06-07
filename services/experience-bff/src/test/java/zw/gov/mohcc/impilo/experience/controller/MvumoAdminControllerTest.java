package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MvumoAdminControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listTemplates_returnsEnvelope() {
        MvumoAdminController controller = new MvumoAdminController(new StubMvumoClient());
        ResponseEntity<Map<String, Object>> response = controller.listTemplates("req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void createTemplate_returns201() {
        MvumoAdminController controller = new MvumoAdminController(new StubMvumoClient());
        ResponseEntity<Map<String, Object>> response = controller.createTemplate(
                "req-2",
                "corr-2",
                Map.of("templateKey", "registry-assisted-consent", "title", "Registry consent"));
        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    private static final class StubMvumoClient extends MvumoServiceClient {
        StubMvumoClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode listTemplates() {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("templateKey", "registry-assisted-consent"));
            return arr;
        }

        @Override
        public JsonNode createTemplate(Map<String, Object> body) {
            return mapper.createObjectNode().put("templateKey", body.get("templateKey").toString());
        }
    }
}
