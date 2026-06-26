package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Coverage for {@link FacilityModeController} (GAP-2). Stateless composer that
 * fetches the C4 FacilityModeContext read-model from TUSO (single SoR) and, when
 * a live PCT facility UUID is supplied, overlays queue depth. Tests cover:
 * happy-path composition from TUSO; TUSO upstream failure → 502; null context → 404.
 *
 * <p>Follows the existing BFF controller-test convention: plain JUnit, controller
 * invoked directly, downstream clients subclassed with overrides.</p>
 */
class FacilityModeControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void getContextComposesFromTusoOnSuccess() {
        FacilityModeController controller =
                new FacilityModeController(new SuccessTusoClient(), stubPctClient());

        // No pctFacilityId → no PCT overlay path exercised.
        ResponseEntity<Map<String, Object>> response =
                controller.getContext(42L, null, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertNotNull(data);
        assertEquals("facility-mode-context-v1", data.get("contract"));
        assertEquals("TUSO", data.get("source"));
        assertNotNull(data.get("context"));
    }

    @Test
    void getContextReturnsBadGatewayWhenTusoFails() {
        FacilityModeController controller =
                new FacilityModeController(new FailingTusoClient(), stubPctClient());

        ResponseEntity<Map<String, Object>> response =
                controller.getContext(42L, null, "req-2", "corr-2");

        assertEquals(502, response.getStatusCode().value());
        assertEquals("FACILITY_MODE_CONTEXT_UNAVAILABLE",
                ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void getContextReturnsNotFoundWhenContextNull() {
        FacilityModeController controller =
                new FacilityModeController(new NullContextTusoClient(), stubPctClient());

        ResponseEntity<Map<String, Object>> response =
                controller.getContext(99L, null, "req-3", "corr-3");

        assertEquals(404, response.getStatusCode().value());
        assertEquals("FACILITY_NOT_FOUND",
                ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void getSetupReturnsBadGatewayWhenTusoFails() {
        FacilityModeController controller =
                new FacilityModeController(new FailingTusoClient(), stubPctClient());

        ResponseEntity<Map<String, Object>> response =
                controller.getSetup(42L, "req-4", "corr-4");

        assertEquals(502, response.getStatusCode().value());
        assertEquals("FACILITY_SETUP_UNAVAILABLE",
                ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    // ── stub clients ──

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static JsonNode node(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** PCT client is never invoked in these tests (no pctFacilityId), so a bare stub suffices. */
    private static PctServiceClient stubPctClient() {
        return new PctServiceClient(new RestTemplate(), endpoints(), MAPPER);
    }

    private static final class SuccessTusoClient extends TusoServiceClient {
        private SuccessTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getFacilityModeContext(long facilityId) {
            return node("""
                    {"facilityId":42,"setupState":"LIVE","visibility":"FULL"}
                    """);
        }
    }

    private static final class FailingTusoClient extends TusoServiceClient {
        private FailingTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getFacilityModeContext(long facilityId) {
            throw new RuntimeException("tuso unavailable");
        }

        @Override
        public JsonNode getFacilitySetupState(long facilityId) {
            throw new RuntimeException("tuso unavailable");
        }
    }

    private static final class NullContextTusoClient extends TusoServiceClient {
        private NullContextTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getFacilityModeContext(long facilityId) {
            return null;
        }
    }
}
