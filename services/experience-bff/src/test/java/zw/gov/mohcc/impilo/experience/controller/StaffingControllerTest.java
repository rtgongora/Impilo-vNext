package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Staffing surfaces after the repoint off tuso.
 *
 * <p>The handlers called {@code tuso /v1/staffing/*}, which no service serves. Rostering is
 * vashandi's, so the failure code and the upstream both change — and an empty week must now be
 * distinguishable from a broken one, which the previous implementation could not do.</p>
 */
class StaffingControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void rosterWeekReturnsBadGatewayWhenVashandiUnavailable() {
        StaffingController controller = new StaffingController(new FailingVashandiClient());

        ResponseEntity<Map<String, Object>> response = controller.rosterWeek(
                "tenant-1", "req-1", "corr-1", "fac-1", "2026-05-11", null);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("VASHANDI_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void onCallReturnsBadGatewayWhenVashandiUnavailable() {
        StaffingController controller = new StaffingController(new FailingVashandiClient());

        ResponseEntity<Map<String, Object>> response = controller.listOnCall(
                "tenant-1", "req-2", "corr-2", "fac-1", "2026-05-11");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("VASHANDI_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    /**
     * The defect this closes: the previous handlers returned {@code data: []} both when the
     * upstream answered with an empty list and when it answered with nothing at all, so "no shifts
     * rostered this week" and "the call produced nothing" were indistinguishable on screen. A
     * genuinely empty week is a 200 with an empty array; a failure is a 502.
     */
    @Test
    void anEmptyWeekIsAnEmptyWeekNotAFailure() {
        StaffingController controller = new StaffingController(new EmptyVashandiClient());

        ResponseEntity<Map<String, Object>> response = controller.rosterWeek(
                "tenant-1", "req-3", "corr-3", "fac-1", "2026-05-11", null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("data") instanceof JsonNode node && node.isArray()
                        && node.isEmpty(),
                "an empty roster must surface as an empty array, not as an upstream failure");
    }

    @Test
    void swapDecisionRejectsAnUnknownStatus() {
        StaffingController controller = new StaffingController(new EmptyVashandiClient());

        try {
            controller.patchSwap(java.util.UUID.randomUUID(), "tenant-1", "req-4", "corr-4",
                    new StaffingController.PatchSwapRequest("MAYBE", null));
            org.junit.jupiter.api.Assertions.fail("an unknown decision must be refused");
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            assertEquals(400, ex.getStatusCode().value());
        }
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class FailingVashandiClient extends VashandiServiceClient {
        private FailingVashandiClient() {
            super(new RestTemplate(), endpoints(), MAPPER);
        }

        @Override
        public JsonNode getRosterWeek(String facilityId, String weekStart) {
            throw new RuntimeException("vashandi unavailable");
        }

        @Override
        public JsonNode listOnCall(String facilityId, String weekStart) {
            throw new RuntimeException("vashandi unavailable");
        }
    }

    private static final class EmptyVashandiClient extends VashandiServiceClient {
        private EmptyVashandiClient() {
            super(new RestTemplate(), endpoints(), MAPPER);
        }

        @Override
        public JsonNode getRosterWeek(String facilityId, String weekStart) {
            return MAPPER.createArrayNode();
        }

        @Override
        public JsonNode listOnCall(String facilityId, String weekStart) {
            return MAPPER.createArrayNode();
        }
    }
}
