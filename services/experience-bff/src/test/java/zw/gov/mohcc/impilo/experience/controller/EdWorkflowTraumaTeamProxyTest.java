package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WU2 — the ED trauma-team roster/ack/escalate BFF proxies are pure passthroughs to
 * pct-service. These assert the controller forwards to the pct client and wraps the
 * sovereign response as {@code {data:...}} (trust-header forwarding is handled by the
 * RestTemplate interceptor, exercised in the client integration tests).
 */
class EdWorkflowTraumaTeamProxyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private EdWorkflowController controllerWith(PctServiceClient pct) {
        return new EdWorkflowController(
                pct,
                mock(ClinicalKnowledgePlatformClient.class),
                mock(NotificationServiceClient.class),
                mock(SupportServiceClient.class),
                mock(InpatientServiceClient.class));
    }

    @Test
    void traumaTeam_delegatesAndWrapsData() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UUID traumaId = UUID.randomUUID();
        ArrayNode roster = mapper.createArrayNode();
        roster.add(mapper.createObjectNode().put("role", "TRAUMA_SURGEON").put("ack_status", "PENDING"));
        when(pct.edTraumaTeam(traumaId.toString())).thenReturn(roster);

        var resp = controllerWith(pct).traumaTeam(traumaId);

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        JsonNode data = (JsonNode) resp.getBody().get("data");
        assertEquals("TRAUMA_SURGEON", data.get(0).get("role").asText());
        verify(pct).edTraumaTeam(traumaId.toString());
    }

    @Test
    void traumaTeamAck_delegatesWithMemberAndBody() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UUID traumaId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        ObjectNode acked = mapper.createObjectNode();
        acked.put("ack_status", "ACKED");
        when(pct.edTraumaTeamAck(eq(traumaId.toString()), eq(memberId.toString()), any())).thenReturn(acked);

        var resp = controllerWith(pct).traumaTeamAck(traumaId, memberId, Map.of("ackBy", "PROV-1"));

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("ACKED", ((JsonNode) resp.getBody().get("data")).get("ack_status").asText());
        verify(pct).edTraumaTeamAck(eq(traumaId.toString()), eq(memberId.toString()), any());
    }

    @Test
    void traumaTeamEscalate_handlesNullBody() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UUID traumaId = UUID.randomUUID();
        when(pct.edTraumaTeamEscalate(eq(traumaId.toString()), any()))
                .thenReturn(mapper.createObjectNode().put("escalated", 3));

        var resp = controllerWith(pct).traumaTeamEscalate(traumaId, null);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(3, ((JsonNode) resp.getBody().get("data")).get("escalated").asInt());
        verify(pct).edTraumaTeamEscalate(eq(traumaId.toString()), any());
    }

    @Test
    void bloodReadiness_delegatesWithOrderId() {
        PctServiceClient pct = mock(PctServiceClient.class);
        ObjectNode readiness = mapper.createObjectNode();
        readiness.put("status", "BLOCKED");
        readiness.put("blocker", "needs RESERVED + COMPATIBLE");
        when(pct.edBloodReadiness("ord-1")).thenReturn(readiness);

        var resp = controllerWith(pct).bloodReadiness("ord-1");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("BLOCKED", ((JsonNode) resp.getBody().get("data")).get("status").asText());
        verify(pct).edBloodReadiness("ord-1");
    }

    @Test
    void preArrival_delegatesWithFacility() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UUID fac = UUID.randomUUID();
        ArrayNode board = mapper.createArrayNode();
        board.add(mapper.createObjectNode().put("status", "PRE_ARRIVAL").put("arrival_mode", "AMBULANCE"));
        when(pct.edPreArrival(fac)).thenReturn(board);

        var resp = controllerWith(pct).preArrival(fac);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(1, ((JsonNode) resp.getBody().get("data")).size());
        verify(pct).edPreArrival(fac);
    }

    // W4d: a pct 422 (triage NOT_TRIAGEABLE / incomplete) must surface AS a 422, not collapse to
    // a 502 that reads as an outage — an unassessed patient is a "complete the assessment" prompt.
    @Test
    void triage_422FromPct_surfacesAs422NotBadGateway() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UUID visitId = UUID.randomUUID();
        HttpClientErrorException notTriageable = HttpClientErrorException.create(
                HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", HttpHeaders.EMPTY,
                "not_triageable: assess and repeat".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        when(pct.edVisitPost(eq(visitId.toString()), eq("/triage"), any())).thenThrow(notTriageable);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controllerWith(pct).triage(visitId, Map.of("triageSystem", "WHO_IITT")));

        assertEquals(422, ex.getStatusCode().value());
        assertNotNull(ex.getReason());
        assertTrue(ex.getReason().contains("not_triageable"), "reason should carry the upstream message");
    }

    // A genuine 5xx / transport failure from pct still surfaces as 502 — only 4xx is passed through.
    @Test
    void triage_transportFailureFromPct_stillBecomesBadGateway() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UUID visitId = UUID.randomUUID();
        when(pct.edVisitPost(eq(visitId.toString()), eq("/triage"), any()))
                .thenThrow(new RuntimeException("connection refused"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controllerWith(pct).triage(visitId, Map.of()));

        assertEquals(502, ex.getStatusCode().value());
    }
}
