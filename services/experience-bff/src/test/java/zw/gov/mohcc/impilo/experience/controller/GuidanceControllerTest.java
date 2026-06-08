package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoConsentServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GuidanceControllerTest {

    @Test
    void askGuidanceForwardsRouteContextToGuidanceService() {
        CapturingGuidanceClient client = new CapturingGuidanceClient();
        GuidanceController controller = new GuidanceController(client, new StubConsentClient());

        var response = controller.askGuidance(
                "tenant-1",
                "req-ctx",
                "corr-ctx",
                Map.of(
                        "question", "What is next?",
                        "personalized", true,
                        "context", Map.of("routePath", "/ehr/42", "journey", "PROVIDER")));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("/ehr/42", client.lastContext.get("routePath"));
        assertEquals("What is next?", client.lastQuestion);
    }

    @Test
    void askGuidanceReturnsBadGatewayWhenGuidanceUnavailable() {
        GuidanceController controller = new GuidanceController(
                new FailingGuidanceClient(),
                new StubConsentClient());

        var response = controller.askGuidance(
                "tenant-1",
                "req-1",
                "corr-1",
                Map.of("question", "hello", "personalized", true));

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("GUIDANCE_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static final class CapturingGuidanceClient extends GuidanceServiceClient {
        private String lastQuestion;
        private Map<String, Object> lastContext = Map.of();

        private CapturingGuidanceClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode ask(
                String question, boolean personalized, Map<String, Object> context) {
            lastQuestion = question;
            lastContext = context;
            return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("response", "ok");
        }
    }

    private static final class FailingGuidanceClient extends GuidanceServiceClient {
        private FailingGuidanceClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode ask(String question, boolean personalized) {
            throw new RuntimeException("guidance unavailable");
        }
    }

    private static final class StubConsentClient extends TshepoConsentServiceClient {
        private StubConsentClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }
    }
}
