package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * TUSO is the system of record for duty state, so both halves matter: what the BFF returns when
 * TUSO records the change, and what it returns when TUSO cannot be reached.
 */
class ShiftControllerTest {

    // Built on the closed-loopback test endpoint, not the live dev port: MockRestServiceServer
    // intercepts before the wire, and testServiceEndpoints() is unreachable by construction.
    //
    // These are tuso's real routes (ShiftController at /v1/internal/facilities/{facilityId},
    // ShiftLifecycleController at /v1/internal/shifts). Shift start is facility-scoped because a
    // shift happens somewhere; ending one only needs the shift. The flat /v1/shifts these tests
    // used to expect was never served by any service, so going on duty always failed.
    private static final String TUSO_FACILITIES =
            ServiceClientConfig.UNREACHABLE_TEST_ENDPOINT + "/v1/internal/facilities";
    private static final String TUSO_SHIFTS =
            ServiceClientConfig.UNREACHABLE_TEST_ENDPOINT + "/v1/internal/shifts";

    // ── TUSO reachable ────────────────────────────────────────────────

    @Test
    void startShift_passesTheShiftTusoOpenedStraightThrough() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(TUSO_FACILITIES + "/fac-1/start-shift"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"facility_id\":\"fac-1\",\"workspace_id\":\"ws-1\","
                                          + "\"user_id\":\"user-1\",\"tenant_id\":\"tenant-1\"}"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"shift-77\",\"status\":\"ACTIVE\"}}",
                        MediaType.APPLICATION_JSON));

        ResponseEntity<Map<String, Object>> response = controller(rt).startShift(
                "tenant-1", "pod-1", "req-1", "corr-1", null, "actor-1",
                Map.of("facilityId", "fac-1", "workspaceId", "ws-1", "userId", "user-1"));

        assertEquals(201, response.getStatusCode().value());
        // The shift id is TUSO's, not one the BFF minted, and the payload is TUSO's own shape —
        // the BFF does not reshape it into a JSON:API id/attributes envelope.
        JsonNode data = assertInstanceOf(JsonNode.class, response.getBody().get("data"));
        assertEquals("shift-77", data.get("id").asText());
        assertEquals("ACTIVE", data.get("status").asText());
        server.verify();
    }

    @Test
    void handoverShift_sendsTheNotesToTusoAndReturnsWhatTusoRecorded() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(TUSO_SHIFTS + "/shift-9/end"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        "{\"handover_notes\":\"OPD queue stable; 3 patients waiting\"}"))
                .andRespond(withSuccess(
                        "{\"data\":{\"id\":\"shift-9\",\"status\":\"ENDED\","
                        + "\"handoverNotes\":\"OPD queue stable; 3 patients waiting\"}}",
                        MediaType.APPLICATION_JSON));

        ResponseEntity<Map<String, Object>> response = controller(rt).handoverShift(
                "tenant-1", "pod-1", "req-2", "corr-2", null,
                Map.of("shiftId", "shift-9", "notes", "OPD queue stable; 3 patients waiting"));

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = assertInstanceOf(JsonNode.class, response.getBody().get("data"));
        assertEquals("shift-9", data.get("id").asText());
        assertEquals("ENDED", data.get("status").asText());
        assertEquals("OPD queue stable; 3 patients waiting", data.get("handoverNotes").asText());
        server.verify();
    }

    @Test
    void handoverShift_echoesTheEndedShiftWhenTusoAcceptsWithoutABody() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(TUSO_SHIFTS + "/shift-9/end"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        ResponseEntity<Map<String, Object>> response = controller(rt).handoverShift(
                "tenant-1", "pod-1", "req-3", "corr-3", null, Map.of("shiftId", "shift-9"));

        // Synthesising {id, ENDED} here is not the old local fallback: TUSO answered 2xx, so the
        // handover *is* recorded — an empty body only means it had nothing to echo back.
        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("id", "shift-9", "status", "ENDED"), response.getBody().get("data"));
        server.verify();
    }

    @Test
    void endShift_forwardsHandoverNotesAndReturnsTusosShift() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(TUSO_SHIFTS + "/shift-9/end"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"handover_notes\":\"ward quiet\"}"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"shift-9\",\"status\":\"ENDED\"}}",
                        MediaType.APPLICATION_JSON));

        ResponseEntity<Map<String, Object>> response = controller(rt).endShift(
                "shift-9", "tenant-1", "pod-1", "req-4", "corr-4", null,
                Map.of("handoverNotes", "ward quiet"));

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = assertInstanceOf(JsonNode.class, response.getBody().get("data"));
        assertEquals("ENDED", data.get("status").asText());
        server.verify();
    }

    // ── TUSO unreachable ──────────────────────────────────────────────

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

    private static ShiftController controller(RestTemplate rt) {
        return new ShiftController(new TusoServiceClient(rt, endpoints()));
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
