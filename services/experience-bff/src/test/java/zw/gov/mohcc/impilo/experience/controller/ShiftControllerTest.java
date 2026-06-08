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

class ShiftControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void startShift_returnsLocalFallbackWhenTusoUnavailable() {
        ShiftController controller = new ShiftController(new UnavailableTusoClient());
        ResponseEntity<Map<String, Object>> response = controller.startShift(
                "tenant-1", "pod-1", "req-1", "corr-1", null, "actor-1",
                Map.of("facilityId", "fac-1", "workspaceId", "ws-1", "userId", "user-1"));

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void handoverShift_recordsNotesAndEndsShift() {
        ShiftController controller = new ShiftController(new UnavailableTusoClient());
        ResponseEntity<Map<String, Object>> response = controller.handoverShift(
                "tenant-1", "pod-1", "req-2", "corr-2", null,
                Map.of("shiftId", "shift-9", "notes", "OPD queue stable; 3 patients waiting"));

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("shift-9", data.get("id"));
        Map<?, ?> attrs = (Map<?, ?>) data.get("attributes");
        assertEquals("HANDED_OVER", attrs.get("status"));
        assertEquals("OPD queue stable; 3 patients waiting", attrs.get("handoverNotes"));
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
    }

}
