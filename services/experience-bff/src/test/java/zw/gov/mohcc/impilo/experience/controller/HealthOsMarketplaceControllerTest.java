package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.IntegrationRegistryClient;
import zw.gov.mohcc.impilo.experience.client.MsikaAppsClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthOsMarketplaceControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void launcher_passesThroughSuccessfulMsikaResponse() {
        HealthOsMarketplaceController controller = new HealthOsMarketplaceController(
                new StubMsikaAppsClient(),
                new IntegrationRegistryClient(new RestTemplate(), ServiceClientConfig.testServiceEndpoints()),
                MAPPER);

        ResponseEntity<String> response = controller.launcher("req-1", "corr-1", "fac-001", "PROVIDER");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("[{\"appCode\":\"telemed\"}]", response.getBody());
    }

    @Test
    void launcher_returnsDegradedResponseWhenMsikaReturns403() throws Exception {
        HealthOsMarketplaceController controller = new HealthOsMarketplaceController(
                new FailingMsikaAppsClient(),
                new IntegrationRegistryClient(new RestTemplate(), ServiceClientConfig.testServiceEndpoints()),
                MAPPER);

        ResponseEntity<String> response = controller.launcher("req-2", "corr-2", "fac-001", null);

        assertEquals(200, response.getStatusCode().value());
        JsonNode body = MAPPER.readTree(response.getBody());
        assertTrue(body.get("data").isArray());
        assertEquals(0, body.get("data").size());
        JsonNode meta = body.get("meta");
        assertEquals(true, meta.get("degraded").asBoolean());
        assertEquals("msika-apps-service", meta.get("upstream").asText());
        assertEquals(403, meta.get("status").asInt());
        assertNotNull(meta.get("guidance").asText());
    }

    @Test
    void launcher_mainLauncherPathStillGracefulViaHealthOsLauncherController() {
        HealthOsLauncherController launcherController = new HealthOsLauncherController(
                new FailingMsikaAppsClient(),
                MAPPER,
                new zw.gov.mohcc.impilo.experience.service.HealthOsLauncherCatalogService(MAPPER));

        ResponseEntity<java.util.Map<String, Object>> response =
                launcherController.listApps(null, null, "fac-001", null);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(((java.util.List<?>) response.getBody().get("apps")).size() >= 31);
    }

    private static final class StubMsikaAppsClient extends MsikaAppsClient {
        StubMsikaAppsClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public ResponseEntity<String> launcher(String facilityId, String roles) {
            return ResponseEntity.ok("[{\"appCode\":\"telemed\"}]");
        }
    }

    private static final class FailingMsikaAppsClient extends MsikaAppsClient {
        FailingMsikaAppsClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public ResponseEntity<String> launcher(String facilityId, String roles) {
            throw HttpClientErrorException.create(
                    HttpStatus.FORBIDDEN,
                    "Forbidden",
                    null,
                    null,
                    null);
        }
    }
}
