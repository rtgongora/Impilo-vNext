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

    @Test
    void v11MetadataLanguages_returnsProxyEnvelope() {
        LearningController controller = new LearningController(
                new StubLearningClient(),
                new StubNotificationClient(),
                new RestTemplate(),
                "http://localhost:8265");
        ResponseEntity<Map<String, Object>> response = controller.v11MetadataLanguages("tenant-1");
        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().containsKey("data"));
    }

    /** W3: the live-linkage join fields flow through the sessions passthrough unmodified. */
    @Test
    void listSessions_passesLiveLinkageFieldsThrough() {
        LearningController controller = new LearningController(
                new StubLearningClient(),
                new StubNotificationClient(),
                new RestTemplate(),
                "http://localhost:8265");
        ResponseEntity<Map<String, Object>> response = controller.listSessions("tenant-1", 50);
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        JsonNode item = data.get("items").get(0);
        assertEquals("LIVE", item.get("sessionMode").asText());
        assertEquals("11111111-1111-1111-1111-111111111111", item.get("liveEventId").asText());
        assertEquals("/live/event/11111111-1111-1111-1111-111111111111", item.get("joinPath").asText());
    }

    @Test
    void completionRules_roundTripThroughPassthrough() {
        LearningController controller = new LearningController(
                new StubLearningClient(),
                new StubNotificationClient(),
                new RestTemplate(),
                "http://localhost:8265");
        ResponseEntity<Map<String, Object>> listed =
                controller.listCompletionRules("tenant-1", "22222222-2222-2222-2222-222222222222");
        assertEquals(200, listed.getStatusCode().value());
        assertEquals(true, listed.getBody().containsKey("data"));

        ResponseEntity<Map<String, Object>> upserted = controller.upsertCompletionRule(
                "tenant-1", "22222222-2222-2222-2222-222222222222",
                Map.of("ruleType", "ATTENDANCE_THRESHOLD", "thresholdValue", 30));
        assertEquals(200, upserted.getStatusCode().value());

        ResponseEntity<Map<String, Object>> confirmed =
                controller.facilitatorConfirm("tenant-1", "33333333-3333-3333-3333-333333333333");
        assertEquals(200, confirmed.getStatusCode().value());
        assertEquals(true, confirmed.getBody().containsKey("data"));
    }

    private static final class StubLearningClient extends LearningServiceClient {
        private static final ObjectMapper mapper = new ObjectMapper();

        StubLearningClient() {
            super(new RestTemplate(), learningProps());
        }

        @Override
        public JsonNode getV11(String relativePath, Map<String, Object> queryParams) {
            if ("metadata/languages".equals(relativePath)) {
                return mapper.createObjectNode()
                        .set("items", mapper.createArrayNode()
                                .add(mapper.createObjectNode()
                                        .put("code", "en")
                                        .put("label", "English")
                                        .put("nativeLabel", "English")));
            }
            if ("sessions".equals(relativePath)) {
                return mapper.createObjectNode()
                        .set("items", mapper.createArrayNode()
                                .add(mapper.createObjectNode()
                                        .put("id", "s-1")
                                        .put("sessionMode", "LIVE")
                                        .put("liveEventId", "11111111-1111-1111-1111-111111111111")
                                        .put("joinPath", "/live/event/11111111-1111-1111-1111-111111111111")));
            }
            return mapper.createObjectNode().put("status", "OK");
        }

        @Override
        public JsonNode postV11(String relativePath, Map<String, Object> body) {
            return mapper.createObjectNode().put("status", "OK").put("path", relativePath);
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
