package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ED intake writes and the diagnostics ten-state machine's acting/closing transitions existed on
 * pct-service and were reachable from nothing: the pre-arrival board could be read but not written,
 * and the estate's only ED safety event ({@code pct.ed.critical_result}) had no route by which the
 * clinician who acted on it could say so.
 */
class EdWorkflowDiagnosticsProxyTest {

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
    void preArrivalUpsert_isWritable_notJustReadable() {
        PctServiceClient pct = mock(PctServiceClient.class);
        ObjectNode created = mapper.createObjectNode();
        created.put("status", "PRE_ARRIVAL");
        created.put("arrival_mode", "AMBULANCE");
        when(pct.upsertEdPreArrival(any())).thenReturn(created);

        var resp = controllerWith(pct).upsertPreArrival(Map.of("arrivalMode", "AMBULANCE"));

        assertEquals(201, resp.getStatusCode().value());
        assertEquals("PRE_ARRIVAL", ((JsonNode) resp.getBody().get("data")).get("status").asText());
        verify(pct).upsertEdPreArrival(any());
    }

    @Test
    void emsArrival_delegates() {
        PctServiceClient pct = mock(PctServiceClient.class);
        when(pct.edArrivalFromEms(any())).thenReturn(mapper.createObjectNode().put("visit_id", "V-1"));

        var resp = controllerWith(pct).arrival(Map.of("preArrivalId", UUID.randomUUID().toString()));

        assertEquals(201, resp.getStatusCode().value());
        verify(pct).edArrivalFromEms(any());
    }

    @Test
    void orderDiagnostic_toleratesAnAbsentBody() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UUID visitId = UUID.randomUUID();
        when(pct.orderEdDiagnostic(eq(visitId), any())).thenReturn(mapper.createObjectNode().put("state", "ORDERED"));

        var resp = controllerWith(pct).orderDiagnostic(visitId, null);

        assertEquals(201, resp.getStatusCode().value());
        verify(pct).orderEdDiagnostic(eq(visitId), any());
    }

    @Test
    void actOnCriticalResult_isReachable() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UUID linkId = UUID.randomUUID();
        when(pct.actOnEdDiagnostic(eq(linkId), any()))
                .thenReturn(mapper.createObjectNode().put("state", "ACTED").put("acted_at", "2026-07-30T03:00:00Z"));

        var resp = controllerWith(pct).actOnDiagnostic(linkId, Map.of("actionTaken", "escalated to registrar"));

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("ACTED", ((JsonNode) resp.getBody().get("data")).get("state").asText());
    }

    @Test
    void reconcileCritical_delegates() {
        PctServiceClient pct = mock(PctServiceClient.class);
        when(pct.reconcileEdCriticalResult(any())).thenReturn(mapper.createObjectNode().put("reconciled", 2));

        var resp = controllerWith(pct).reconcileCritical(Map.of("facilityId", UUID.randomUUID().toString()));

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(2, ((JsonNode) resp.getBody().get("data")).get("reconciled").asInt());
    }

    /**
     * pct refuses to close a critical order that was never acted on. That refusal is the entire point
     * of the state machine, so it has to arrive at the clinician as a 409 carrying its own reason —
     * collapsed to a 502 it would read as "the service is down" and invite a retry that also fails.
     */
    @Test
    void closingACriticalOrderThatWasNeverActedOn_surfacesPctsRefusal_notAnOutage() {
        PctServiceClient pct = mock(PctServiceClient.class);
        UUID linkId = UUID.randomUUID();
        when(pct.closeEdDiagnostic(eq(linkId), any())).thenThrow(HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY,
                "critical result not acted on".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controllerWith(pct).closeDiagnostic(linkId, Map.of("closedBy", "PROV-1")));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("critical result not acted on"));
    }

    @Test
    void transportFailure_stillBecomesBadGateway() {
        PctServiceClient pct = mock(PctServiceClient.class);
        when(pct.reconcileEdCriticalResult(any())).thenThrow(new RuntimeException("connection refused"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controllerWith(pct).reconcileCritical(Map.of()));

        assertEquals(502, ex.getStatusCode().value());
    }
}
