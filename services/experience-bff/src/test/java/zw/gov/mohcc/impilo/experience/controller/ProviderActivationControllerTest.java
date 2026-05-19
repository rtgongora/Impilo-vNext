package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProviderActivationControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void returnsBadGatewayWhenVarapiUnavailable() {
        ProviderActivationController controller = new ProviderActivationController(new FailingVarapiClient());

        ResponseEntity<Map<String, Object>> response = controller.listProvidersByActor("tenant-1", "req-1", "corr-1", "actor-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("VARAPI_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void returnsProviderFromHealthIdLookup() {
        ProviderActivationController controller = new ProviderActivationController(new SuccessVarapiClient());

        ResponseEntity<Map<String, Object>> response = controller.listProvidersByActor("tenant-1", "req-2", "corr-2", "actor-2");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("data"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class FailingVarapiClient extends VarapiServiceClient {
        private FailingVarapiClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getProviderByHealthId(String healthId) {
            throw new RuntimeException("varapi unavailable");
        }
    }

    private static final class SuccessVarapiClient extends VarapiServiceClient {
        private SuccessVarapiClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getProviderByHealthId(String healthId) {
            try {
                return MAPPER.readTree("""
                        {"providerId":"PRV-1","displayName":"Dr. Real"}
                        """);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
