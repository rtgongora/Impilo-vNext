package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.AiModelRegistryServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiModelRegistryControllerTest {

    @Test
    void listModelsFailClosesWhenRegistryUnavailable() {
        AiModelRegistryController controller = new AiModelRegistryController(new FailingAiClient());

        var response = controller.listModels("req-ai-model-1", "corr-ai-model-1", 0, 50);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("AI_MODEL_REGISTRY_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void listModelsFailsClosedWhenAiRegistryReturnsEmptyPayload() {
        AiModelRegistryController controller = new AiModelRegistryController(new EmptyAiClient());

        ResponseEntity<Map<String, Object>> response = controller.listModels("req-1", "corr-1", 0, 20);

        assertEquals(502, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("AI_MODEL_REGISTRY_UNAVAILABLE", error.get("code"));
    }

    @Test
    void driftEventsRequiresModelId() {
        AiModelRegistryController controller = new AiModelRegistryController(new FailingAiClient());

        var response = controller.listDriftEvents("req-ai-model-2", "corr-ai-model-2", null, 0, 50);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("MODEL_ID_REQUIRED", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void getModelReturnsSuccessEnvelope() {
        AiModelRegistryController controller = new AiModelRegistryController(new SuccessAiClient());

        var response = controller.getModel("model-1", "req-ai-model-3", "corr-ai-model-3");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("model-1", ((JsonNode) response.getBody().get("data")).get("id").asText());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class FailingAiClient extends AiModelRegistryServiceClient {
        private FailingAiClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listModels() {
            throw new RuntimeException("ai registry unavailable");
        }
    }

    private static final class EmptyAiClient extends AiModelRegistryServiceClient {
        private EmptyAiClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listModels() {
            return null;
        }
    }

    private static final class SuccessAiClient extends AiModelRegistryServiceClient {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private SuccessAiClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getModel(String modelId) {
            return MAPPER.createObjectNode().put("id", modelId).put("name", "Risk Model");
        }
    }
}
