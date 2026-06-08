package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.LearningServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.config.LearningServiceRuntimeProperties;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LearningControllerTest {

    @Test
    void v11Catalog_returnsProxyEnvelope() {
        LearningController controller = new LearningController(
                new StubLearningClient(),
                new StubNotificationClient(),
                new RestTemplate(),
                "http://localhost:8265");
        ResponseEntity<Map<String, Object>> response = controller.v11Catalog("tenant-1", null, null, null, null, null, null, 5);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void myLearning_returnsProxyEnvelope() {
        LearningController controller = new LearningController(
                new StubLearningClient(),
                new StubNotificationClient(),
                new RestTemplate(),
                "http://localhost:8265");
        ResponseEntity<Map<String, Object>> response =
                controller.myLearning("tenant-1", "PERSON", "actor-1");
        assertEquals(200, response.getStatusCode().value());
    }

    private static final class StubLearningClient extends LearningServiceClient {
        private static final ObjectMapper mapper = new ObjectMapper();

        StubLearningClient() {
            super(new RestTemplate(), learningProps());
        }

        @Override
        public JsonNode getV11(String relativePath, Map<String, Object> queryParams) {
            return mapper.createObjectNode().put("status", "OK");
        }

        @Override
        public JsonNode getV11Catalog(
                String status, String category, String level, Boolean cpdEligible, Boolean mandatory, String language, int limit) {
            return mapper.createObjectNode().put("limit", limit).set("items", mapper.createArrayNode());
        }

        private static LearningServiceRuntimeProperties learningProps() {
            LearningServiceRuntimeProperties props = new LearningServiceRuntimeProperties();
            props.setBaseUrl("http://localhost:8101");
            return props;
        }
    }

    private static final class StubNotificationClient extends NotificationServiceClient {
        StubNotificationClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }
    }
}
