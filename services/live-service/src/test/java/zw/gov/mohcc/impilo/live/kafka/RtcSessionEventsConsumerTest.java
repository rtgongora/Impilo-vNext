package zw.gov.mohcc.impilo.live.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.live.core.AttendanceService;
import zw.gov.mohcc.impilo.live.core.LiveEventService;
import zw.gov.mohcc.impilo.live.core.ReplayService;
import zw.gov.mohcc.impilo.live.domain.LiveEventStatus;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventAttendanceEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventSessionEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventSessionRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RtcSessionEventsConsumerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String RTC_SESSION_ID = "33333333-3333-3333-3333-333333333333";

    @Mock private LiveEventSessionRepository sessionRepository;
    @Mock private LiveEventRepository eventRepository;
    @Mock private LiveEventService eventService;
    @Mock private AttendanceService attendanceService;
    @Mock private ReplayService replayService;

    private RtcSessionEventsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RtcSessionEventsConsumer(new ObjectMapper(),
                sessionRepository, eventRepository, eventService, attendanceService, replayService);
    }

    // ── session.started ──────────────────────────────────────────────────────

    @Test
    void sessionStarted_transitionsScheduledEventToLive() {
        LiveEventSessionEntity session = sessionRow();
        stubResolution(session, event(LiveEventStatus.SCHEDULED));
        lenient().when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onSessionStarted(payload("impilo.rtc.session.started.v1", "LIVE", null));

        verify(eventService).goLive(TENANT_ID, EVENT_ID, RtcSessionEventsConsumer.RTC_ACTOR);
        // media truth is reflected on the session row too
        ArgumentCaptor<LiveEventSessionEntity> saved = ArgumentCaptor.forClass(LiveEventSessionEntity.class);
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().getStartedAt()).isNotNull();
        assertThat(saved.getValue().getHealthStatus()).isEqualTo("HEALTHY");
    }

    @Test
    void sessionStarted_alreadyLive_isIdempotentNoOp() {
        LiveEventSessionEntity session = sessionRow();
        session.setStartedAt(OffsetDateTime.now());
        stubResolution(session, event(LiveEventStatus.LIVE));

        consumer.onSessionStarted(payload("impilo.rtc.session.started.v1", "LIVE", null));

        verify(eventService, never()).goLive(any(), any(), anyString());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void sessionStarted_ignoresOtherOwningServicesSilently() {
        consumer.onSessionStarted(payload("impilo.rtc.session.started.v1", "PCT", null));
        consumer.onSessionStarted(payload("impilo.rtc.session.started.v1", "KHULUMA", null));

        verifyNoInteractions(eventService, attendanceService, eventRepository);
    }

    @Test
    void sessionStarted_unknownSession_isSkipped() {
        when(sessionRepository.findFirstByProviderRoomIdOrderByCreatedAtDesc(RTC_SESSION_ID))
                .thenReturn(Optional.empty());

        consumer.onSessionStarted(payload("impilo.rtc.session.started.v1", "LIVE", null));

        verifyNoInteractions(eventService, attendanceService);
    }

    @Test
    void sessionStarted_resolvesByOwningRefWhenRoomLookupMisses() {
        when(sessionRepository.findFirstByProviderRoomIdOrderByCreatedAtDesc(RTC_SESSION_ID))
                .thenReturn(Optional.empty());
        when(eventRepository.findById(EVENT_ID))
                .thenReturn(Optional.of(event(LiveEventStatus.SCHEDULED)));

        consumer.onSessionStarted(payload("impilo.rtc.session.started.v1", "LIVE", EVENT_ID.toString()));

        verify(eventService).goLive(TENANT_ID, EVENT_ID, RtcSessionEventsConsumer.RTC_ACTOR);
    }

    // ── session.finished ─────────────────────────────────────────────────────

    @Test
    void sessionFinished_endsLiveEvent() {
        LiveEventSessionEntity session = sessionRow();
        stubResolution(session, event(LiveEventStatus.LIVE));
        lenient().when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onSessionFinished(payload("impilo.rtc.session.finished.v1", "LIVE", null));

        verify(eventService).end(TENANT_ID, EVENT_ID, RtcSessionEventsConsumer.RTC_ACTOR);
        ArgumentCaptor<LiveEventSessionEntity> saved = ArgumentCaptor.forClass(LiveEventSessionEntity.class);
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().getEndedAt()).isNotNull();
        assertThat(saved.getValue().getHealthStatus()).isEqualTo("ENDED");
    }

    @Test
    void sessionFinished_alreadyEnded_isIdempotentNoOp() {
        LiveEventSessionEntity session = sessionRow();
        session.setEndedAt(OffsetDateTime.now());
        stubResolution(session, event(LiveEventStatus.ENDED));

        consumer.onSessionFinished(payload("impilo.rtc.session.finished.v1", "LIVE", null));

        verify(eventService, never()).end(any(), any(), anyString());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void sessionFinished_scheduledEvent_doesNotForceInvalidTransition() {
        stubResolution(sessionRow(), event(LiveEventStatus.SCHEDULED));
        lenient().when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        consumer.onSessionFinished(payload("impilo.rtc.session.finished.v1", "LIVE", null));

        verify(eventService, never()).end(any(), any(), anyString());
    }

    // ── participant.joined / participant.left ────────────────────────────────

    @Test
    void participantJoined_recordsAttendanceViaAttendanceService() {
        stubResolution(sessionRow(), event(LiveEventStatus.LIVE));

        consumer.onParticipantJoined(participantPayload(
                "impilo.rtc.participant.joined.v1", "LIVE", "provider-77"));

        verify(attendanceService).join(TENANT_ID, EVENT_ID, "provider-77",
                RtcSessionEventsConsumer.RTC_PARTICIPANT_TYPE);
    }

    @Test
    void participantJoined_withoutIdentity_isSkipped() {
        stubResolution(sessionRow(), event(LiveEventStatus.LIVE));

        consumer.onParticipantJoined(payload("impilo.rtc.participant.joined.v1", "LIVE", null));

        verifyNoInteractions(attendanceService);
    }

    @Test
    void participantLeft_closesOpenAttendanceInterval() {
        stubResolution(sessionRow(), event(LiveEventStatus.LIVE));
        LiveEventAttendanceEntity attendance = new LiveEventAttendanceEntity();
        attendance.setJoinedAt(OffsetDateTime.now().minusMinutes(40));
        when(attendanceService.get(TENANT_ID, EVENT_ID, "provider-77")).thenReturn(attendance);

        consumer.onParticipantLeft(participantPayload(
                "impilo.rtc.participant.left.v1", "LIVE", "provider-77"));

        verify(attendanceService).leave(TENANT_ID, EVENT_ID, "provider-77");
    }

    @Test
    void participantLeft_alreadyClosed_doesNotDoubleCountMinutes() {
        stubResolution(sessionRow(), event(LiveEventStatus.LIVE));
        LiveEventAttendanceEntity attendance = new LiveEventAttendanceEntity();
        attendance.setJoinedAt(OffsetDateTime.now().minusMinutes(40));
        attendance.setLeftAt(OffsetDateTime.now());
        when(attendanceService.get(TENANT_ID, EVENT_ID, "provider-77")).thenReturn(attendance);

        consumer.onParticipantLeft(participantPayload(
                "impilo.rtc.participant.left.v1", "LIVE", "provider-77"));

        verify(attendanceService, never()).leave(any(), any(), anyString());
    }

    @Test
    void participantLeft_withoutJoinRecord_isSkipped() {
        stubResolution(sessionRow(), event(LiveEventStatus.LIVE));
        when(attendanceService.get(TENANT_ID, EVENT_ID, "ghost"))
                .thenThrow(new IllegalArgumentException("Attendance not found"));

        consumer.onParticipantLeft(participantPayload(
                "impilo.rtc.participant.left.v1", "LIVE", "ghost"));

        verify(attendanceService, never()).leave(any(), any(), anyString());
    }

    // ── backstage child rooms (W6) ───────────────────────────────────────────

    @Test
    void backstageSession_neverDrivesEventLifecycleOrAttendance() {
        // owningService=LIVE + owningRef=eventId like the main room, but the
        // '-backstage' session id marks it as the linked child room: a producer
        // warming up backstage must not flip a SCHEDULED event LIVE.
        String backstage = """
                {"sessionId":"%s-backstage","sessionMode":"LIVE_EVENT","owningService":"LIVE",
                 "owningRef":"%s","roomName":"impilo-live-%s-backstage",
                 "occurredAt":"2026-07-04T10:00:00Z"}
                """.formatted(RTC_SESSION_ID, EVENT_ID, RTC_SESSION_ID);

        consumer.onSessionStarted(backstage);
        consumer.onSessionFinished(backstage);
        consumer.onParticipantJoined(backstage);

        verifyNoInteractions(eventService, attendanceService, replayService);
        verifyNoInteractions(sessionRepository, eventRepository);
    }

    // ── resilience ───────────────────────────────────────────────────────────

    @Test
    void poisonMessages_areSwallowedNotThrown() {
        assertThatCode(() -> {
            consumer.onSessionStarted("not json at all {{{");
            consumer.onSessionFinished("");
            consumer.onParticipantJoined("[]");
            consumer.onParticipantLeft("42");
            consumer.onRecordingAvailable("not json");
        }).doesNotThrowAnyException();
    }

    @Test
    void downstreamFailure_isSwallowedNotThrown() {
        stubResolution(sessionRow(), event(LiveEventStatus.SCHEDULED));
        when(eventService.goLive(any(), any(), anyString()))
                .thenThrow(new IllegalStateException("Invalid live event transition"));

        assertThatCode(() ->
                consumer.onSessionStarted(payload("impilo.rtc.session.started.v1", "LIVE", null)))
                .doesNotThrowAnyException();
    }

    // ── recording.available (W1 replay pipeline) ─────────────────────────────

    @Test
    void recordingAvailable_invokesReplayPipelineWithParsedArtifact() {
        LiveEventSessionEntity session = sessionRow();
        LiveEventEntity event = event(LiveEventStatus.ENDED);
        stubResolution(session, event);

        consumer.onRecordingAvailable(recordingPayload("LIVE"));

        ArgumentCaptor<ReplayService.RecordingAvailable> recording =
                ArgumentCaptor.forClass(ReplayService.RecordingAvailable.class);
        verify(replayService).onRecordingAvailable(
                org.mockito.ArgumentMatchers.same(event),
                org.mockito.ArgumentMatchers.same(session),
                org.mockito.ArgumentMatchers.eq(RtcSessionEventsConsumer.RTC_ACTOR),
                recording.capture());
        assertThat(recording.getValue().egressId()).isEqualTo("egress-9");
        assertThat(recording.getValue().storageBucket()).isEqualTo("impilo-recordings");
        assertThat(recording.getValue().storageKey()).isEqualTo("rtc/sessions/" + RTC_SESSION_ID + "/egress-9.mp4");
        assertThat(recording.getValue().durationSeconds()).isEqualTo(1800L);
        assertThat(recording.getValue().sizeBytes()).isEqualTo(52000000L);
    }

    @Test
    void recordingAvailable_ignoresOtherOwningServicesSilently() {
        consumer.onRecordingAvailable(recordingPayload("PCT"));

        verifyNoInteractions(replayService, eventService, attendanceService, eventRepository);
    }

    @Test
    void recordingAvailable_withoutSessionRow_isSkipped() {
        // owningRef fallback resolves the event but there is no session row to stamp.
        when(sessionRepository.findFirstByProviderRoomIdOrderByCreatedAtDesc(RTC_SESSION_ID))
                .thenReturn(Optional.empty());
        when(eventRepository.findById(EVENT_ID))
                .thenReturn(Optional.of(event(LiveEventStatus.ENDED)));

        consumer.onRecordingAvailable(recordingPayload("LIVE", EVENT_ID.toString()));

        verifyNoInteractions(replayService);
    }

    @Test
    void recordingAvailable_replayServiceFailure_isSwallowedNotThrown() {
        stubResolution(sessionRow(), event(LiveEventStatus.ENDED));
        org.mockito.Mockito.doThrow(new IllegalStateException("Invalid live event transition"))
                .when(replayService).onRecordingAvailable(any(), any(), anyString(), any());

        assertThatCode(() -> consumer.onRecordingAvailable(recordingPayload("LIVE")))
                .doesNotThrowAnyException();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void stubResolution(LiveEventSessionEntity session, LiveEventEntity event) {
        when(sessionRepository.findFirstByProviderRoomIdOrderByCreatedAtDesc(RTC_SESSION_ID))
                .thenReturn(Optional.of(session));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    private static LiveEventSessionEntity sessionRow() {
        LiveEventSessionEntity session = new LiveEventSessionEntity();
        session.setId(UUID.randomUUID());
        session.setEventId(EVENT_ID);
        session.setProviderRoomId(RTC_SESSION_ID);
        return session;
    }

    private static LiveEventEntity event(LiveEventStatus status) {
        LiveEventEntity event = new LiveEventEntity();
        event.setId(EVENT_ID);
        event.setTenantId(TENANT_ID);
        event.setStatus(status.name());
        return event;
    }

    private static String payload(String type, String owningService, String owningRef) {
        return """
                {"sessionId":"%s","sessionMode":"LIVE_EVENT","owningService":"%s",%s
                 "roomName":"impilo-live-room","eventId":"evt-1","occurredAt":"2026-07-04T10:00:00Z",
                 "unknownField":{"nested":true},"type":"%s"}
                """.formatted(RTC_SESSION_ID, owningService,
                owningRef == null ? "\"owningRef\":null," : "\"owningRef\":\"" + owningRef + "\",",
                type);
    }

    private static String recordingPayload(String owningService) {
        return recordingPayload(owningService, null);
    }

    private static String recordingPayload(String owningService, String owningRef) {
        return """
                {"sessionId":"%s","sessionMode":"LIVE_EVENT","owningService":"%s",%s
                 "roomName":"impilo-live-room","egressId":"egress-9",
                 "storageBucket":"impilo-recordings","storageKey":"rtc/sessions/%s/egress-9.mp4",
                 "durationSeconds":1800,"sizeBytes":52000000,"eventId":"evt-3",
                 "occurredAt":"2026-07-04T11:00:00Z","type":"impilo.rtc.recording.available.v1"}
                """.formatted(RTC_SESSION_ID, owningService,
                owningRef == null ? "\"owningRef\":null," : "\"owningRef\":\"" + owningRef + "\",",
                RTC_SESSION_ID);
    }

    private static String participantPayload(String type, String owningService, String identity) {
        return """
                {"sessionId":"%s","sessionMode":"LIVE_EVENT","owningService":"%s","owningRef":null,
                 "roomName":"impilo-live-room","eventId":"evt-2","occurredAt":"2026-07-04T10:05:00Z",
                 "participantIdentity":"%s","type":"%s"}
                """.formatted(RTC_SESSION_ID, owningService, identity, type);
    }
}
