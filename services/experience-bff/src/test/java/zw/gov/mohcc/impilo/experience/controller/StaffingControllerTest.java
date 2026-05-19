package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StaffingControllerTest {

    @Test
    void rosterWeekReturnsBadGatewayWhenTusoUnavailable() {
        StaffingController controller = new StaffingController(new FailingTusoClient());

        ResponseEntity<Map<String, Object>> response = controller.rosterWeek(
                "tenant-1", "req-1", "corr-1", "fac-1", "2026-05-11", null);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("TUSO_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void onCallReturnsBadGatewayWhenTusoUnavailable() {
        StaffingController controller = new StaffingController(new FailingTusoClient());

        ResponseEntity<Map<String, Object>> response = controller.listOnCall(
                "tenant-1", "req-2", "corr-2", "fac-1", "2026-05-11");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("TUSO_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class FailingTusoClient extends TusoServiceClient {
        private FailingTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode getRosterWeek(String facilityId, String weekStart, String workspaceId) {
            throw new RuntimeException("tuso unavailable");
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode listOnCall(String facilityId, String weekStart) {
            throw new RuntimeException("tuso unavailable");
        }
    }
}
