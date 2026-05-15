package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProviderFinancingControllerTest {

    @Test
    void listContractsFailClosesWithTypedError() {
        ProviderFinancingController controller = new ProviderFinancingController(new FailingCoverageClient());
        ResponseEntity<Map<String, Object>> response = controller.listContracts(
                null, null, null, "req-1", "corr-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COVERAGE_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listNetworksFailClosesWithTypedError() {
        ProviderFinancingController controller = new ProviderFinancingController(new FailingCoverageClient());
        ResponseEntity<Map<String, Object>> response = controller.listNetworks(
                null, null, "req-2", "corr-2");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COVERAGE_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listNetworkMembersFailClosesWithTypedError() {
        ProviderFinancingController controller = new ProviderFinancingController(new FailingCoverageClient());
        ResponseEntity<Map<String, Object>> response = controller.listNetworkMembers(
                "network-1", "req-3", "corr-3");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COVERAGE_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        assertEquals("req-3", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void createContractFailClosesWithTypedError() {
        ProviderFinancingController controller = new ProviderFinancingController(new FailingCoverageClient());
        ResponseEntity<Map<String, Object>> response = controller.createContract(
                "req-4", "corr-4", Map.of("provider_id", "p-1"));

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COVERAGE_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        assertEquals("corr-4", ((Map<?, ?>) response.getBody().get("meta")).get("correlation_id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class FailingCoverageClient extends CoverageServiceClient {
        FailingCoverageClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listProviderContracts(String providerId, String payerId, String status) {
            throw new RuntimeException("coverage unavailable");
        }

        @Override
        public JsonNode listProviderNetworks(String payerId, String status) {
            throw new RuntimeException("coverage unavailable");
        }

        @Override
        public JsonNode listNetworkMembers(String networkId) {
            throw new RuntimeException("coverage unavailable");
        }

        @Override
        public JsonNode createProviderContract(Map<String, Object> body) {
            throw new RuntimeException("coverage unavailable");
        }
    }
}
