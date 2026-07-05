package zw.gov.mohcc.impilo.rtc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;
import zw.gov.mohcc.impilo.rtc.model.RtcStreamStartRequest;
import zw.gov.mohcc.impilo.rtc.persistence.InMemoryRtcSessionPersistence;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplateRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RtcStreamOutServiceTest {

    private RtcGatewayProperties properties;
    private InMemoryRtcSessionPersistence sessions;
    private LiveKitEgressClient egressClient;
    private RtcOutboxPublisher outbox;
    private RtcStreamOutService service;

    @BeforeEach
    void setUp() {
        properties = new RtcGatewayProperties();
        sessions = new InMemoryRtcSessionPersistence();
        egressClient = mock(LiveKitEgressClient.class);
        outbox = mock(RtcOutboxPublisher.class);
        service = new RtcStreamOutService(properties, sessions, egressClient, outbox,
                new SessionTemplateRegistry());
        sessions.save(session("session-1", "PROVISIONED"));
    }

    private RtcSessionRecord session(String id, String status) {
        return new RtcSessionRecord(
                id, "tenant-1", "LIVEKIT", "impilo-live-" + id, "wss://livekit.test",
                status, "LIVE_EVENT", "LIVE", "event-9", null,
                null, null, null, null, null, "rtc:impilo-live-" + id,
                Map.of(), Map.of(), Instant.now(), Instant.now());
    }

    private RtcStreamStartRequest request(String role, List<String> urls) {
        return new RtcStreamStartRequest("producer-1", role, urls, null);
    }

    @Test
    void streamOutIsFlagGatedOffByDefault() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.start("session-1", request("PRODUCER", List.of("rtmp://target/live"))));

        assertEquals(true, ex.getMessage().contains("stream-out-enabled"));
        verify(egressClient, never()).startRoomCompositeStream(anyString(), any(), anyList());
    }

    @Test
    void startsStreamEgressForTemplateRecordingRole() {
        properties.getGateway().setStreamOutEnabled(true);
        when(egressClient.startRoomCompositeStream(eq("impilo-live-session-1"), anyString(), anyList()))
                .thenReturn(new LiveKitEgressClient.EgressStart("EG_1", null));

        Map<String, Object> out = service.start("session-1",
                request("PRODUCER", List.of("rtmp://target/live", "rtmps://backup/live")));

        assertEquals("EG_1", out.get("egressId"));
        assertEquals(2, out.get("targetCount"));
        assertEquals("REQUESTED", out.get("status"));
        // LIVE_EVENT defaultLayout is "stage" — used when the request omits a layout.
        verify(egressClient).startRoomCompositeStream("impilo-live-session-1", "stage",
                List.of("rtmp://target/live", "rtmps://backup/live"));
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outbox).append(eq("RtcSession"), eq("session-1"),
                eq(RtcStreamOutService.EVT_STREAM_REQUESTED), payload.capture());
        assertEquals("event-9", payload.getValue().get("owningRef"));
    }

    @Test
    void refusesRolesOutsideTheTemplateCapability() {
        properties.getGateway().setStreamOutEnabled(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.start("session-1", request("AUDIENCE", List.of("rtmp://target/live"))));
        verify(egressClient, never()).startRoomCompositeStream(anyString(), any(), anyList());
    }

    @Test
    void refusesNonRtmpTargets() {
        properties.getGateway().setStreamOutEnabled(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.start("session-1", request("HOST", List.of("https://not-rtmp.example"))));
    }

    @Test
    void refusesEndedSessions() {
        properties.getGateway().setStreamOutEnabled(true);
        sessions.save(session("session-2", "ENDED"));

        assertThrows(IllegalArgumentException.class,
                () -> service.start("session-2", request("HOST", List.of("rtmp://target/live"))));
    }

    @Test
    void stopWorksEvenWithFlagOffAndRequiresEgressId() {
        Map<String, Object> out = service.stop("session-1", "EG_9");

        assertEquals("STOP_REQUESTED", out.get("status"));
        verify(egressClient).stopEgress("EG_9");
        verify(outbox).append(eq("RtcSession"), eq("session-1"),
                eq(RtcStreamOutService.EVT_STREAM_STOPPED), any());

        assertThrows(IllegalArgumentException.class, () -> service.stop("session-1", " "));
    }
}
