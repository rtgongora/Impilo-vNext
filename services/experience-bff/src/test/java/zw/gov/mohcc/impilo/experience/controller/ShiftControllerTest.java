package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShiftControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * These two previously asserted the "local shift fallback": a 201 carrying a random shift id
     * and a 200 reporting status HANDED_OVER, both produced when TUSO was unreachable. The BFF is
     * stateless, so neither was recorded anywhere — the fabricated id resolved to nothing and the
     * handover transferred no responsibility. The contract is now that an unrecorded duty change
     * is reported as a failure.
     */
    @Test
    void startShift_failsWhenTusoUnavailableRatherThanMintingALocalShift() {
        ShiftController controller = new ShiftController(new UnavailableTusoClient());
        ResponseEntity<Map<String, Object>> response = controller.startShift(
                "tenant-1", "pod-1", "req-1", "corr-1", null, "actor-1",
                Map.of("facilityId", "fac-1", "workspaceId", "ws-1", "userId", "user-1"));

        assertEquals(502, response.getStatusCode().value());
        assertEquals("shift_not_started", response.getBody().get("error"));
        assertNull(response.getBody().get("data"),
                "no shift was created, so there is no shift resource to return");
    }

    @Test
    void handoverShift_failsWhenTusoUnavailableRatherThanReportingHandedOver() {
        ShiftController controller = new ShiftController(new UnavailableTusoClient());
        ResponseEntity<Map<String, Object>> response = controller.handoverShift(
                "tenant-1", "pod-1", "req-2", "corr-2", null,
                Map.of("shiftId", "shift-9", "notes", "OPD queue stable; 3 patients waiting"));

        assertEquals(502, response.getStatusCode().value());
        assertEquals("shift_handover_not_recorded", response.getBody().get("error"));
        assertNull(response.getBody().get("data"));
        assertNotNull(response.getBody().get("meta"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class UnavailableTusoClient extends TusoServiceClient {
        UnavailableTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode startShift(Map<String, Object> shiftData) {
            throw new RuntimeException("tuso unavailable");
        }

        @Override
        public JsonNode endShift(String shiftId, Map<String, Object> endData) {
            throw new RuntimeException("tuso unavailable");
        }
    }

}
