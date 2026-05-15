package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MobileScheduleControllerTest {

    @Test
    void listUpcomingShiftsReturnsBadGatewayWhenTusoUnavailable() {
        MobileScheduleController controller = new MobileScheduleController(new FailingTusoClient());

        var response = controller.listUpcomingShifts("tenant-1", "req-1", "corr-1", "actor-1", 0, 20);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("TUSO_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static final class FailingTusoClient extends TusoServiceClient {
        private FailingTusoClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode getCurrentShift(String userId) {
            throw new RuntimeException("tuso unavailable");
        }
    }
}
