package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RegistryGeoLocalityControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void districtsReturnsBadGatewayOnUpstreamFailure() {
        RegistryGeoLocalityController controller = new RegistryGeoLocalityController(new FailingTusoClient());

        ResponseEntity<Map<String, Object>> response = controller.listDistricts("HA", "req-1", "corr-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("TUSO_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void districtsReturnsDataOnSuccess() throws Exception {
        RegistryGeoLocalityController controller = new RegistryGeoLocalityController(new SuccessTusoClient());

        ResponseEntity<Map<String, Object>> response = controller.listDistricts("HA", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("data"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class FailingTusoClient extends TusoServiceClient {
        private FailingTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listZwDistricts(String provinceCode) {
            throw new RuntimeException("tuso unavailable");
        }
    }

    private static final class SuccessTusoClient extends TusoServiceClient {
        private SuccessTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listZwDistricts(String provinceCode) {
            try {
                return MAPPER.readTree("""
                        [
                          {"code":"HA-01","name":"Harare"}
                        ]
                        """);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
