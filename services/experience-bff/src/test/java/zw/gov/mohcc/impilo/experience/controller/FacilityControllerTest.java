package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.config.BffFacilitiesProperties;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FacilityControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void liveModeReturnsBadGatewayWhenTusoSearchFails() {
        BffFacilitiesProperties props = new BffFacilitiesProperties();
        props.setMode(BffFacilitiesProperties.Mode.live);
        FacilityController controller = new FacilityController(new FailingTusoClient(), props);

        ResponseEntity<Map<String, Object>> response = controller.listFacilities(
                "tenant-1", "req-1", "corr-1", 0, 20, null, null, null, "harare");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("TUSO_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void liveModeReturnsMappedPayloadWhenTusoSucceeds() throws Exception {
        BffFacilitiesProperties props = new BffFacilitiesProperties();
        props.setMode(BffFacilitiesProperties.Mode.live);
        FacilityController controller = new FacilityController(new SuccessTusoClient(), props);

        ResponseEntity<Map<String, Object>> response = controller.listFacilities(
                "tenant-1", "req-2", "corr-2", 0, 20, null, null, null, "harare");

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
        public JsonNode searchFacilities(Map<String, Object> requestBody) {
            throw new RuntimeException("tuso unavailable");
        }
    }

    private static final class SuccessTusoClient extends TusoServiceClient {
        private SuccessTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode searchFacilities(Map<String, Object> requestBody) {
            try {
                return MAPPER.readTree("""
                        {
                          "items":[
                            {"id":101,"name":"Harare Central","code":"HCH","type":"CENTRAL","district":"Harare","province":"Harare","status":"ACTIVE"}
                          ],
                          "totalElements":1
                        }
                        """);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
