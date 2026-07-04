package zw.gov.mohcc.impilo.rtc.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.rtc.RtcGatewayProperties;
import zw.gov.mohcc.impilo.rtc.RtcOutboxPublisher;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipantRecord;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;
import zw.gov.mohcc.impilo.rtc.persistence.InMemoryRtcParticipantPersistence;
import zw.gov.mohcc.impilo.rtc.persistence.InMemoryRtcSessionPersistence;
import zw.gov.mohcc.impilo.rtc.persistence.RtcRecordingPersistence;
import zw.gov.mohcc.impilo.rtc.persistence.RtcTelemetryPersistence;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RtcWebhookTranslatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ROOM = "impilo-telemedicine-session-1";

    private InMemoryRtcSessionPersistence sessions;
    private InMemoryRtcParticipantPersistence participants;
    private RtcTelemetryPersistence telemetry;
    private RtcRecordingPersistence recordings;
    private RtcOutboxPublisher outbox;
    private RtcWebhookTranslator translator;

    @BeforeEach
    void setUp() {
        sessions = new InMemoryRtcSessionPersistence();
        sessions.save(new RtcSessionRecord(
                "session-1", "tenant-1", "LIVEKIT", ROOM, "wss://livekit.test",
                "PROVISIONED", "TELEMEDICINE", "PCT", "encounter:enc-1",
                "patient-1", "provider-1", "enc-1", "ref-1", "consent-1", "rtc:" + ROOM,
                Map.of(), Map.of(), Instant.now(), Instant.now()));
        participants = new InMemoryRtcParticipantPersistence();
        telemetry = mock(RtcTelemetryPersistence.class);
        when(telemetry.insertEventIfNew(any(), any(), anyString(), any(), any(), any(), any())).thenReturn(true);
        recordings = mock(RtcRecordingPersistence.class);
        outbox = mock(RtcOutboxPublisher.class);
        RtcGatewayProperties properties = new RtcGatewayProperties();
        properties.getRecording().getS3().setBucket("impilo-recordings");
        translator = new RtcWebhookTranslator(sessions, telemetry, recordings, participants, outbox, properties);
    }

    @Test
    void roomStartedMarksSessionAndPublishesStartedEvent() throws Exception {
        RtcWebhookTranslator.Outcome outcome = translator.handle(event("room_started", "EV_1", null));

        assertEquals(RtcWebhookTranslator.Outcome.PROCESSED, outcome);
        verify(telemetry).markSessionStarted(eq("session-1"), any(Instant.class));
        Map<String, Object> payload = capturePayload(RtcWebhookTranslator.EVT_SESSION_STARTED);
        assertCommonEnvelope(payload, "EV_1");
    }

    @Test
    void roomFinishedMarksEndAndPublishesFinishedEvent() throws Exception {
        translator.handle(event("room_finished", "EV_2", null));

        verify(telemetry).markSessionEnded(eq("session-1"), any(Instant.class));
        assertCommonEnvelope(capturePayload(RtcWebhookTranslator.EVT_SESSION_FINISHED), "EV_2");
    }

    @Test
    void participantJoinedWritesStatsAndPublishes() throws Exception {
        translator.handle(event("participant_joined", "EV_3", "patient-1"));

        verify(telemetry).recordParticipantJoined(eq("session-1"), eq("patient-1"), any(Instant.class), any());
        Map<String, Object> payload = capturePayload(RtcWebhookTranslator.EVT_PARTICIPANT_JOINED);
        assertEquals("patient-1", payload.get("participantIdentity"));
    }

    @Test
    void participantJoinedMarksLobbyRowConnected() throws Exception {
        participants.upsert(new RtcParticipantRecord(
                "session-1", "patient-1", "Patient", "PATIENT",
                RtcParticipantRecord.STATE_ADMITTED, "FULL", Instant.now(), Instant.now(), "provider-1", null));

        translator.handle(event("participant_joined", "EV_30", "patient-1"));

        assertEquals(RtcParticipantRecord.STATE_CONNECTED,
                participants.find("session-1", "patient-1").orElseThrow().state());
    }

    @Test
    void participantLeftMarksLobbyRowLeft() throws Exception {
        participants.upsert(new RtcParticipantRecord(
                "session-1", "patient-1", "Patient", "PATIENT",
                RtcParticipantRecord.STATE_CONNECTED, "FULL", Instant.now(), Instant.now(), "provider-1", null));

        translator.handle(event("participant_left", "EV_31", "patient-1"));

        assertEquals(RtcParticipantRecord.STATE_LEFT,
                participants.find("session-1", "patient-1").orElseThrow().state());
    }

    @Test
    void participantJoinedWithoutLobbyRowIsANoOpForRoster() throws Exception {
        translator.handle(event("participant_joined", "EV_32", "stranger-1"));

        assertEquals(0, participants.findBySession("session-1").size());
    }

    @Test
    void participantLeftUpsertsStatsWithDisconnectReason() throws Exception {
        JsonNode event = MAPPER.readTree("""
                {"event":"participant_left","id":"EV_4","createdAt":1750000000,
                 "room":{"name":"%s"},
                 "participant":{"identity":"patient-1","disconnectReason":"CLIENT_INITIATED"}}
                """.formatted(ROOM));

        translator.handle(event);

        verify(telemetry).recordParticipantLeft(
                eq("session-1"), eq("patient-1"), any(Instant.class), eq("CLIENT_INITIATED"), any());
        Map<String, Object> payload = capturePayload(RtcWebhookTranslator.EVT_PARTICIPANT_LEFT);
        assertEquals("CLIENT_INITIATED", payload.get("disconnectReason"));
        assertEquals("patient-1", payload.get("participantIdentity"));
    }

    @Test
    void trackPublishedCarriesSourceForScreenShareDetection() throws Exception {
        JsonNode event = MAPPER.readTree("""
                {"event":"track_published","id":"EV_5","createdAt":1750000000,
                 "room":{"name":"%s"},
                 "participant":{"identity":"provider-1"},
                 "track":{"sid":"TR_1","type":"VIDEO","source":"SCREEN_SHARE"}}
                """.formatted(ROOM));

        translator.handle(event);

        Map<String, Object> payload = capturePayload(RtcWebhookTranslator.EVT_TRACK_PUBLISHED);
        assertEquals("SCREEN_SHARE", payload.get("source"));
        assertEquals("TR_1", payload.get("trackSid"));
    }

    @Test
    void trackUnpublishedMapsToUnpublishedEvent() throws Exception {
        JsonNode event = MAPPER.readTree("""
                {"event":"track_unpublished","id":"EV_6","createdAt":1750000000,
                 "room":{"name":"%s"},
                 "participant":{"identity":"provider-1"},
                 "track":{"sid":"TR_1","type":"VIDEO","source":"CAMERA"}}
                """.formatted(ROOM));

        translator.handle(event);

        Map<String, Object> payload = capturePayload(RtcWebhookTranslator.EVT_TRACK_UNPUBLISHED);
        assertEquals("CAMERA", payload.get("source"));
    }

    @Test
    void egressLifecycleMapsToRecordingEvents() throws Exception {
        translator.handle(egressEvent("egress_started", "EV_7", "EGRESS_ACTIVE"));
        capturePayload(RtcWebhookTranslator.EVT_RECORDING_STARTED);

        translator.handle(egressEvent("egress_updated", "EV_8", "EGRESS_ACTIVE"));
        capturePayload(RtcWebhookTranslator.EVT_RECORDING_UPDATED);

        translator.handle(egressEvent("egress_ended", "EV_9", "EGRESS_COMPLETE"));
        Map<String, Object> available = capturePayload(RtcWebhookTranslator.EVT_RECORDING_AVAILABLE);
        assertEquals("EGRESS_COMPLETE", available.get("egressStatus"));
    }

    @Test
    void failedEgressEndMapsToRecordingFailed() throws Exception {
        translator.handle(egressEvent("egress_ended", "EV_10", "EGRESS_FAILED"));

        Map<String, Object> payload = capturePayload(RtcWebhookTranslator.EVT_RECORDING_FAILED);
        assertEquals("EGRESS_FAILED", payload.get("egressStatus"));
    }

    @Test
    void egressStartedMarksRecordingRowActive() throws Exception {
        translator.handle(egressEvent("egress_started", "EV_20", "EGRESS_ACTIVE"));

        verify(recordings).markActive("session-1", "EG_1");
        capturePayload(RtcWebhookTranslator.EVT_RECORDING_STARTED);
    }

    @Test
    void egressEndedCompletesRecordingAndEnrichesAvailableEvent() throws Exception {
        JsonNode event = MAPPER.readTree("""
                {"event":"egress_ended","id":"EV_21","createdAt":1750000000,
                 "egressInfo":{"egressId":"EG_1","roomName":"%s","status":"EGRESS_COMPLETE",
                   "fileResults":[{"filename":"recordings/%s-1750000000.mp4",
                                   "size":"1048576","duration":"30000000000"}]}}
                """.formatted(ROOM, ROOM));

        translator.handle(event);

        verify(recordings).markEnded(eq("session-1"), eq("EG_1"), eq("COMPLETE"),
                eq("impilo-recordings"), eq("recordings/" + ROOM + "-1750000000.mp4"),
                eq(1048576L), eq(30), any(Instant.class));
        Map<String, Object> payload = capturePayload(RtcWebhookTranslator.EVT_RECORDING_AVAILABLE);
        assertCommonEnvelope(payload, "EV_21");
        assertEquals("EG_1", payload.get("egressId"));
        assertEquals("impilo-recordings", payload.get("storageBucket"));
        assertEquals("recordings/" + ROOM + "-1750000000.mp4", payload.get("storageKey"));
        assertEquals(30, payload.get("durationSeconds"));
        assertEquals(1048576L, payload.get("sizeBytes"));
    }

    @Test
    void failedEgressEndMarksRecordingFailed() throws Exception {
        translator.handle(egressEvent("egress_ended", "EV_22", "EGRESS_FAILED"));

        verify(recordings).markEnded(eq("session-1"), eq("EG_1"), eq("FAILED"),
                isNull(), isNull(), isNull(), isNull(), any(Instant.class));
        capturePayload(RtcWebhookTranslator.EVT_RECORDING_FAILED);
    }

    @Test
    void duplicateEventIsSkippedEntirely() throws Exception {
        when(telemetry.insertEventIfNew(any(), any(), anyString(), any(), any(), any(), any())).thenReturn(false);

        RtcWebhookTranslator.Outcome outcome = translator.handle(event("room_started", "EV_1", null));

        assertEquals(RtcWebhookTranslator.Outcome.DUPLICATE, outcome);
        verifyNoInteractions(outbox);
        verify(telemetry, never()).markSessionStarted(any(), any());
    }

    @Test
    void unknownRoomRecordsRawEventButPublishesNothing() throws Exception {
        JsonNode event = MAPPER.readTree("""
                {"event":"room_started","id":"EV_11","createdAt":1750000000,
                 "room":{"name":"impilo-unknown-room"}}
                """);

        RtcWebhookTranslator.Outcome outcome = translator.handle(event);

        assertEquals(RtcWebhookTranslator.Outcome.UNKNOWN_ROOM, outcome);
        verify(telemetry).insertEventIfNew(isNull(), eq("EV_11"), eq("room_started"),
                isNull(), eq("impilo-unknown-room"), any(), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void egressEventResolvesRoomFromEgressInfo() throws Exception {
        JsonNode event = MAPPER.readTree("""
                {"event":"egress_ended","id":"EV_12","createdAt":1750000000,
                 "egressInfo":{"egressId":"EG_2","roomName":"%s","status":"EGRESS_COMPLETE"}}
                """.formatted(ROOM));

        RtcWebhookTranslator.Outcome outcome = translator.handle(event);

        assertEquals(RtcWebhookTranslator.Outcome.PROCESSED, outcome);
        capturePayload(RtcWebhookTranslator.EVT_RECORDING_AVAILABLE);
    }

    private JsonNode event(String type, String id, String identity) throws Exception {
        String participant = identity == null ? "" : ",\"participant\":{\"identity\":\"" + identity + "\"}";
        return MAPPER.readTree(
                "{\"event\":\"" + type + "\",\"id\":\"" + id + "\",\"createdAt\":1750000000,"
                        + "\"room\":{\"name\":\"" + ROOM + "\"}" + participant + "}");
    }

    private JsonNode egressEvent(String type, String id, String status) throws Exception {
        return MAPPER.readTree(
                "{\"event\":\"" + type + "\",\"id\":\"" + id + "\",\"createdAt\":1750000000,"
                        + "\"room\":{\"name\":\"" + ROOM + "\"},"
                        + "\"egressInfo\":{\"egressId\":\"EG_1\",\"status\":\"" + status + "\"}}");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePayload(String expectedEventType) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outbox).append(eq("RtcSession"), eq("session-1"), eq(expectedEventType), captor.capture());
        return captor.getValue();
    }

    private void assertCommonEnvelope(Map<String, Object> payload, String eventId) {
        assertEquals("session-1", payload.get("sessionId"));
        assertEquals("TELEMEDICINE", payload.get("sessionMode"));
        assertEquals("PCT", payload.get("owningService"));
        assertEquals("encounter:enc-1", payload.get("owningRef"));
        assertEquals(ROOM, payload.get("roomName"));
        assertEquals(eventId, payload.get("eventId"));
        assertNotNull(payload.get("occurredAt"));
    }
}
