package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.DaidzaiServiceClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DaidzaiControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createSosRequest_delegatesAndReturns201() {
        DaidzaiServiceClient client = mock(DaidzaiServiceClient.class);
        ObjectNode created = mapper.createObjectNode();
        created.put("id", "r1");
        created.put("requestReference", "SOS-20260630-AB12");
        when(client.createRequest(any())).thenReturn(created);

        DaidzaiController controller = new DaidzaiController(client);
        var resp = controller.createRequest(Map.of("emergencyCategory", "CARDIAC", "severity", "CRITICAL"));

        assertEquals(201, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals("SOS-20260630-AB12", resp.getBody().get("requestReference").asText());
        verify(client).createRequest(any());
    }

    @Test
    void track_delegatesToGetRequest() {
        DaidzaiServiceClient client = mock(DaidzaiServiceClient.class);
        ObjectNode node = mapper.createObjectNode();
        node.put("status", "RECEIVED");
        when(client.getRequest("r1")).thenReturn(node);

        DaidzaiController controller = new DaidzaiController(client);
        var resp = controller.getRequest("r1");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("RECEIVED", resp.getBody().get("status").asText());
        verify(client).getRequest("r1");
    }

    @Test
    void dispatch_delegatesAndReturns201() {
        DaidzaiServiceClient client = mock(DaidzaiServiceClient.class);
        ObjectNode ev = mapper.createObjectNode();
        ev.put("status", "DISPATCH_REQUESTED");
        when(client.dispatch(eq("inc1"), any())).thenReturn(ev);

        DaidzaiController controller = new DaidzaiController(client);
        var resp = controller.dispatch("inc1", Map.of("note", "go"));

        assertEquals(201, resp.getStatusCode().value());
        assertEquals("DISPATCH_REQUESTED", resp.getBody().get("status").asText());
        verify(client).dispatch(eq("inc1"), any());
    }

    @Test
    void listDisasters_delegates() {
        DaidzaiServiceClient client = mock(DaidzaiServiceClient.class);
        ArrayNode arr = mapper.createArrayNode();
        arr.add(mapper.createObjectNode().put("incidentType", "DISASTER"));
        when(client.listDisasters()).thenReturn(arr);

        DaidzaiController controller = new DaidzaiController(client);
        var resp = controller.listDisasters();

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(1, resp.getBody().size());
        verify(client).listDisasters();
    }

    @Test
    void listRequests_delegatesWithStatus() {
        DaidzaiServiceClient client = mock(DaidzaiServiceClient.class);
        ArrayNode arr = mapper.createArrayNode();
        arr.add(mapper.createObjectNode().put("status", "RECEIVED"));
        when(client.listRequests("RECEIVED")).thenReturn(arr);

        DaidzaiController controller = new DaidzaiController(client);
        var resp = controller.listRequests("RECEIVED");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(1, resp.getBody().size());
        verify(client).listRequests("RECEIVED");
    }

    @Test
    void verifyCallback_delegatesAndReturns200() {
        DaidzaiServiceClient client = mock(DaidzaiServiceClient.class);
        ObjectNode verified = mapper.createObjectNode();
        verified.put("status", "RECEIVED");
        verified.put("callbackVerified", true);
        when(client.verifyCallback("r1")).thenReturn(verified);

        DaidzaiController controller = new DaidzaiController(client);
        var resp = controller.verifyCallback("r1");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("RECEIVED", resp.getBody().get("status").asText());
        assertEquals(true, resp.getBody().get("callbackVerified").asBoolean());
        verify(client).verifyCallback("r1");
    }

    @Test
    void traumaEpisode_delegatesAndReturnsTimeline() {
        DaidzaiServiceClient client = mock(DaidzaiServiceClient.class);
        ObjectNode ep = mapper.createObjectNode();
        ep.put("traumaEpisodeId", "ep-1");
        ep.put("status", "OPEN");
        ep.set("timeline", mapper.createArrayNode());
        when(client.traumaEpisode("ep-1")).thenReturn(ep);

        DaidzaiController controller = new DaidzaiController(client);
        var resp = controller.traumaEpisode("ep-1");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("OPEN", resp.getBody().get("status").asText());
        verify(client).traumaEpisode("ep-1");
    }

    @Test
    void closeDisaster_handlesNullBody() {
        DaidzaiServiceClient client = mock(DaidzaiServiceClient.class);
        when(client.closeDisaster(eq("inc1"), any())).thenReturn(mapper.createObjectNode().put("status", "CLOSED"));

        DaidzaiController controller = new DaidzaiController(client);
        var resp = controller.close("inc1", null);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("CLOSED", resp.getBody().get("status").asText());
        verify(client).closeDisaster(eq("inc1"), any());
    }
}
