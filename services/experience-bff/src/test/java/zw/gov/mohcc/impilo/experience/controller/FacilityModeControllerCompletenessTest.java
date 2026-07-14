package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice coverage for the facility profile-completeness lane on
 * {@link FacilityModeController}: TUSO verdict pass-through on GET, TUSO
 * fail-closed 403 and validation 400 surfaced VERBATIM on POST (status + body),
 * and 502 on transport failure. Follows the existing BFF controller-test
 * convention: plain JUnit, controller invoked directly, downstream clients
 * subclassed with overrides.
 */
class FacilityModeControllerCompletenessTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── GET completeness ──

    @Test
    void getCompletenessPassesTusoVerdictThrough() {
        FacilityModeController controller =
                new FacilityModeController(new CompletenessTusoClient(), stubPctClient());

        ResponseEntity<Map<String, Object>> response =
                controller.getCompleteness(42L, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertNotNull(data);
        assertEquals(false, data.path("complete").asBoolean(true));
        assertEquals("MISSING", data.path("geocodeStatus").asText());
        assertEquals("missing_latitude", data.path("missingFields").path(0).asText());
    }

    @Test
    void getCompletenessReturnsBadGatewayOnTransportFailure() {
        FacilityModeController controller =
                new FacilityModeController(new FailingTusoClient(), stubPctClient());

        ResponseEntity<Map<String, Object>> response =
                controller.getCompleteness(42L, "req-2", "corr-2");

        assertEquals(502, response.getStatusCode().value());
        assertEquals("FACILITY_COMPLETENESS_UNAVAILABLE",
                ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    // ── POST complete-profile ──

    @Test
    void completeProfileReturnsUpdatedVerdictOnSuccess() {
        FacilityModeController controller =
                new FacilityModeController(new CompletenessTusoClient(), stubPctClient());

        ResponseEntity<Map<String, Object>> response = controller.completeProfile(
                42L, Map.of("latitude", -17.83, "longitude", 31.05,
                        "geocodeProvenance", Map.of("source", "DEVICE_GPS")),
                "req-3", "corr-3");

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals(true, data.path("complete").asBoolean(false));
        assertEquals("PRESENT", data.path("geocodeStatus").asText());
    }

    @Test
    void completeProfileSurfacesTusoForbiddenVerbatim() {
        FacilityModeController controller =
                new FacilityModeController(new ForbiddenTusoClient(), stubPctClient());

        ResponseEntity<Map<String, Object>> response = controller.completeProfile(
                42L, Map.of("facilityType", "CLINIC"), "req-4", "corr-4");

        assertEquals(403, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("FACILITY_COMPLETE_PROFILE_REJECTED", error.get("code"));
        assertTrue(((String) error.get("message")).contains("practitioner-in-charge"),
                "TUSO's authz reason must surface verbatim");
    }

    @Test
    void completeProfileSurfacesTusoValidationVerbatim() {
        FacilityModeController controller =
                new FacilityModeController(new BadRequestTusoClient(), stubPctClient());

        ResponseEntity<Map<String, Object>> response = controller.completeProfile(
                42L, Map.of("latitude", -17.83), "req-5", "corr-5");

        assertEquals(400, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("FACILITY_COMPLETE_PROFILE_REJECTED", error.get("code"));
        assertTrue(((String) error.get("message")).contains("latitude and longitude"));
    }

    @Test
    void completeProfileReturnsBadGatewayOnTransportFailure() {
        FacilityModeController controller =
                new FacilityModeController(new FailingTusoClient(), stubPctClient());

        ResponseEntity<Map<String, Object>> response = controller.completeProfile(
                42L, Map.of("facilityType", "CLINIC"), "req-6", "corr-6");

        assertEquals(502, response.getStatusCode().value());
        assertEquals("FACILITY_COMPLETE_PROFILE_UNAVAILABLE",
                ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    // ── stub clients ──

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static PctServiceClient stubPctClient() {
        return new PctServiceClient(new RestTemplate(), endpoints(), MAPPER);
    }

    private static JsonNode node(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final class CompletenessTusoClient extends TusoServiceClient {
        private CompletenessTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getFacilityCompleteness(long facilityId) {
            return node("{\"facilityId\":42,\"complete\":false,"
                    + "\"missingFields\":[\"missing_latitude\",\"missing_longitude\"],"
                    + "\"geocodeStatus\":\"MISSING\"}");
        }

        @Override
        public JsonNode completeFacilityProfile(long facilityId, Map<String, Object> body) {
            return node("{\"facilityId\":42,\"complete\":true,\"missingFields\":[],"
                    + "\"geocodeStatus\":\"PRESENT\"}");
        }
    }

    private static final class ForbiddenTusoClient extends TusoServiceClient {
        private ForbiddenTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode completeFacilityProfile(long facilityId, Map<String, Object> body) {
            throw HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null,
                    ("{\"success\":false,\"error\":{\"message\":\"Actor holds no ACTIVE facility-administrator "
                            + "appointment and is not the active practitioner-in-charge at this facility\"}}")
                            .getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
        }
    }

    private static final class BadRequestTusoClient extends TusoServiceClient {
        private BadRequestTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode completeFacilityProfile(long facilityId, Map<String, Object> body) {
            throw HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null,
                    "{\"success\":false,\"error\":{\"message\":\"latitude and longitude must be submitted together\"}}"
                            .getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
        }
    }

    private static final class FailingTusoClient extends TusoServiceClient {
        private FailingTusoClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getFacilityCompleteness(long facilityId) {
            throw new RuntimeException("connection refused");
        }

        @Override
        public JsonNode completeFacilityProfile(long facilityId, Map<String, Object> body) {
            throw new RuntimeException("connection refused");
        }
    }
}
