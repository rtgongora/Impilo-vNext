package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MobileLabControllerTest {

    @Test
    void listLabOrdersReturnsBadGatewayWhenOrosUnavailable() {
        MobileLabController controller = new MobileLabController(new FailingOrosClient());

        var response = controller.listLabOrders("tenant-1", "req-1", "corr-1", null, "cpid-1", 0, 20);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("OROS_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static final class FailingOrosClient extends OrosServiceClient {
        private FailingOrosClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode getPatientOrders(String cpid) {
            throw new RuntimeException("oros unavailable");
        }
    }
}
