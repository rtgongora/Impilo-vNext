package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.LiveServiceClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Unit tests for the W6 LIVE_EVENT polish relays on the Live BFF proxy:
 * stage management, backstage rooms, and announcements. Mocks the sovereign
 * live client (stateless controller) and asserts proxy wiring, envelope
 * shape, created-status semantics, and the BAD_GATEWAY failure path.
 */
class LiveControllerStageRelayTest {

    private static final String REQ = "req-1";
    private static final String COR = "cor-1";
    private static final String EVENT = "22222222-2222-2222-2222-222222222222";

    private LiveServiceClient client;
    private LiveController controller;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        client = Mockito.mock(LiveServiceClient.class);
        controller = new LiveController(client);
    }

    private ObjectNode node(String key, String value) {
        ObjectNode n = mapper.createObjectNode();
        n.put(key, value);
        return n;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(ResponseEntity<Map<String, Object>> r) {
        return r.getBody();
    }

    // ── Stage management ────────────────────────────────────────────

    @Test
    void requestStage_proxiesBodyAndReturnsCreated() {
        Map<String, Object> payload = Map.of("participantId", "citizen-9", "participantType", "CITIZEN");
        Mockito.when(client.requestStage(EVENT, payload)).thenReturn(node("status", "REQUESTED"));

        ResponseEntity<Map<String, Object>> r = controller.requestStage(EVENT, payload, REQ, COR);

        assertEquals(201, r.getStatusCode().value());
        Mockito.verify(client).requestStage(EVENT, payload);
        assertNotNull(body(r).get("data"));
        assertEquals(Map.of("request_id", REQ, "correlation_id", COR), body(r).get("meta"));
    }

    @Test
    void listStageRequests_forwardsOptionalStatusFilter() {
        Mockito.when(client.listStageRequests(EVENT, "REQUESTED")).thenReturn(mapper.createArrayNode());

        ResponseEntity<Map<String, Object>> r = controller.listStageRequests(EVENT, "REQUESTED", REQ, COR);

        assertEquals(200, r.getStatusCode().value());
        Mockito.verify(client).listStageRequests(EVENT, "REQUESTED");
    }

    @Test
    void approveAndDeny_proxyRequestIdPaths() {
        Mockito.when(client.approveStageRequest(EVENT, "r1")).thenReturn(node("status", "APPROVED"));
        Mockito.when(client.denyStageRequest(EVENT, "r2")).thenReturn(node("status", "DENIED"));

        assertEquals(200, controller.approveStageRequest(EVENT, "r1", REQ, COR).getStatusCode().value());
        assertEquals(200, controller.denyStageRequest(EVENT, "r2", REQ, COR).getStatusCode().value());
        Mockito.verify(client).approveStageRequest(EVENT, "r1");
        Mockito.verify(client).denyStageRequest(EVENT, "r2");
    }

    @Test
    void demote_proxiesParticipantPath() {
        Mockito.when(client.demoteStageParticipant(EVENT, "nurse-2")).thenReturn(node("tier", "AUDIENCE"));

        ResponseEntity<Map<String, Object>> r = controller.demoteStageParticipant(EVENT, "nurse-2", REQ, COR);

        assertEquals(200, r.getStatusCode().value());
        Mockito.verify(client).demoteStageParticipant(EVENT, "nurse-2");
    }

    @Test
    void stageRole_proxiesServerResolvedTier() {
        Mockito.when(client.getStageRole(EVENT, "citizen-9")).thenReturn(node("tier", "AUDIENCE"));

        ResponseEntity<Map<String, Object>> r = controller.getStageRole(EVENT, "citizen-9", REQ, COR);

        assertEquals(200, r.getStatusCode().value());
        assertEquals("AUDIENCE",
                ((com.fasterxml.jackson.databind.JsonNode) body(r).get("data")).get("tier").asText());
    }

    // ── Backstage ───────────────────────────────────────────────────

    @Test
    void backstageJoin_proxiesBodyAndReturnsCreated() {
        Map<String, Object> payload = Map.of("participantId", "producer-1", "participantType", "PROVIDER");
        Mockito.when(client.joinBackstage(EVENT, payload)).thenReturn(node("backstageRoomId", "rtc-1-backstage"));

        ResponseEntity<Map<String, Object>> r = controller.joinBackstage(EVENT, payload, REQ, COR);

        assertEquals(201, r.getStatusCode().value());
        Mockito.verify(client).joinBackstage(EVENT, payload);
    }

    @Test
    void backstageToken_proxiesBody() {
        Map<String, Object> payload = Map.of("participantId", "producer-1");
        Mockito.when(client.backstageToken(EVENT, payload)).thenReturn(node("role", "PRODUCER"));

        ResponseEntity<Map<String, Object>> r = controller.backstageToken(EVENT, payload, REQ, COR);

        assertEquals(200, r.getStatusCode().value());
        Mockito.verify(client).backstageToken(EVENT, payload);
    }

    // ── Announcements ───────────────────────────────────────────────

    @Test
    void announcements_postCreatedAndListOk() {
        Map<String, Object> payload = Map.of(
                "participantId", "host-1", "participantType", "PROVIDER", "message", "Doors open in 5");
        Mockito.when(client.postAnnouncement(EVENT, payload)).thenReturn(node("kind", "ANNOUNCEMENT"));
        Mockito.when(client.listAnnouncements(EVENT)).thenReturn(mapper.createArrayNode());

        assertEquals(201, controller.postAnnouncement(EVENT, payload, REQ, COR).getStatusCode().value());
        assertEquals(200, controller.listAnnouncements(EVENT, REQ, COR).getStatusCode().value());
        Mockito.verify(client).postAnnouncement(EVENT, payload);
        Mockito.verify(client).listAnnouncements(EVENT);
    }

    // ── Failure path ────────────────────────────────────────────────

    @Test
    void downstreamFailure_mapsToBadGatewayEnvelope() {
        Mockito.when(client.getStageRole(eq(EVENT), eq("citizen-9")))
                .thenThrow(new IllegalStateException("live-service unreachable"));

        ResponseEntity<Map<String, Object>> r = controller.getStageRole(EVENT, "citizen-9", REQ, COR);

        assertEquals(502, r.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) body(r).get("error");
        assertEquals("LIVE_UNAVAILABLE", error.get("code"));
    }
}
