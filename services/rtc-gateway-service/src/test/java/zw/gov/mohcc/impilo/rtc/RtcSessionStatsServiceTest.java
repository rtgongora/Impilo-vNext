package zw.gov.mohcc.impilo.rtc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;
import zw.gov.mohcc.impilo.rtc.persistence.InMemoryRtcSessionPersistence;
import zw.gov.mohcc.impilo.rtc.persistence.RtcTelemetryPersistence;
import zw.gov.mohcc.impilo.rtc.persistence.RtcTelemetryPersistence.ParticipantStatRow;
import zw.gov.mohcc.impilo.rtc.persistence.RtcTelemetryPersistence.SessionLifecycle;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RtcSessionStatsServiceTest {

    private InMemoryRtcSessionPersistence sessions;
    private RtcTelemetryPersistence telemetry;
    private RtcSessionStatsService service;

    @BeforeEach
    void setUp() {
        sessions = new InMemoryRtcSessionPersistence();
        telemetry = mock(RtcTelemetryPersistence.class);
        service = new RtcSessionStatsService(sessions, telemetry);
        sessions.save(new RtcSessionRecord(
                "session-1", "tenant-1", "LIVEKIT", "impilo-live-session-1", "wss://livekit.test",
                "PROVISIONED", "LIVE_EVENT", "LIVE", "event-9", null,
                null, null, null, null, null, "rtc:impilo-live-session-1",
                Map.of(), Map.of(), Instant.now(), Instant.now()));
    }

    @Test
    void aggregatesParticipantTelemetryIntoMediaStats() {
        Instant t0 = Instant.parse("2026-07-01T10:00:00Z");
        when(telemetry.findParticipantStats("session-1")).thenReturn(List.of(
                new ParticipantStatRow("alice", t0, t0.plusSeconds(600), 600, null, "CLIENT_INITIATED"),
                new ParticipantStatRow("alice", t0.plusSeconds(700), null, null, null, null),
                new ParticipantStatRow("bob", t0, t0.plusSeconds(300), 300, null, "SIGNAL_CLOSE"),
                new ParticipantStatRow("carol", t0, t0.plusSeconds(900), 900, null, "CLIENT_INITIATED")));
        when(telemetry.findSessionLifecycle("session-1")).thenReturn(Optional.of(
                new SessionLifecycle(t0, null, 3)));

        Map<String, Object> stats = service.stats("session-1");

        assertEquals(4L, stats.get("totalJoins"));
        assertEquals(3L, stats.get("distinctParticipants"));
        assertEquals(1L, stats.get("activeParticipants"));
        assertEquals(600.0, stats.get("avgDurationSeconds"));
        assertEquals(900, stats.get("maxDurationSeconds"));
        assertEquals(1L, stats.get("abnormalDisconnects"));
        assertEquals(Map.of("CLIENT_INITIATED", 2L, "SIGNAL_CLOSE", 1L), stats.get("disconnectReasons"));
        assertEquals(3, stats.get("peakParticipants"));
        assertEquals("LIVE_EVENT", stats.get("sessionMode"));
        assertEquals("event-9", stats.get("owningRef"));
        // connection_quality is never webhook-populated — key must be honest-absent when empty.
        assertFalse(stats.containsKey("connectionQuality"));
    }

    @Test
    void emptyTelemetryYieldsZeroedStats() {
        when(telemetry.findParticipantStats("session-1")).thenReturn(List.of());
        when(telemetry.findSessionLifecycle("session-1")).thenReturn(Optional.empty());

        Map<String, Object> stats = service.stats("session-1");

        assertEquals(0L, stats.get("totalJoins"));
        assertEquals(0L, stats.get("distinctParticipants"));
        assertEquals(0.0, stats.get("avgDurationSeconds"));
        assertEquals(0L, stats.get("abnormalDisconnects"));
    }

    @Test
    void unknownSessionIsNotFound() {
        assertThrows(RtcNotFoundException.class, () -> service.stats("nope"));
    }
}
