package zw.gov.mohcc.impilo.live.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.live.domain.LiveEventStatus;
import zw.gov.mohcc.impilo.live.integration.DocumentServiceClient;
import zw.gov.mohcc.impilo.live.media.LiveMediaProvider;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventSessionEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventSessionRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplayServiceTest {

    @Mock
    private LiveEventService eventService;
    @Mock
    private LiveEventSessionRepository sessionRepository;
    @Mock
    private LiveMediaProvider mediaProvider;
    @Mock
    private DocumentServiceClient documentServiceClient;

    private ReplayService replayService;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        replayService = new ReplayService(eventService, sessionRepository, mediaProvider, documentServiceClient);
    }

    @Test
    void processReplayTransitionsToPublishedWhenPlaybackReady() {
        LiveEventEntity event = event("ENDED", true);
        LiveEventSessionEntity session = session("room-1", null);

        when(sessionRepository.findByEventIdOrderByCreatedAtDesc(eventId)).thenReturn(List.of(session));
        when(eventService.get(tenantId, eventId)).thenReturn(event);
        when(mediaProvider.getPlaybackUrl("room-1", "auto-recording-" + session.getId()))
                .thenReturn("dev-replay://localhost/live/room-1/rec");
        when(eventService.beginReplayProcessing(tenantId, eventId, "host")).thenAnswer(inv -> {
            event.setStatus(LiveEventStatus.PROCESSING_REPLAY.name());
            return event;
        });
        when(eventService.publishReplay(tenantId, eventId, "host")).thenAnswer(inv -> {
            event.setStatus(LiveEventStatus.PUBLISHED_REPLAY.name());
            return event;
        });

        Map<String, Object> result = replayService.processReplay(tenantId, eventId, "host");

        assertThat(result.get("status")).isEqualTo(LiveEventStatus.PUBLISHED_REPLAY.name());
        assertThat(result.get("playbackUrl")).isEqualTo("dev-replay://localhost/live/room-1/rec");
        verify(eventService).beginReplayProcessing(tenantId, eventId, "host");
        verify(eventService).publishReplay(tenantId, eventId, "host");
    }

    @Test
    void onSessionEndSkipsWhenReplayNotAllowed() {
        LiveEventEntity event = event("ENDED", false);
        LiveEventSessionEntity session = session("room-2", "rec-1");

        when(eventService.get(tenantId, eventId)).thenReturn(event);

        replayService.onSessionEnd(tenantId, eventId, "host", session);

        verify(eventService).get(tenantId, eventId);
        verify(sessionRepository, org.mockito.Mockito.never()).save(any());
    }

    // ── onRecordingAvailable (W1 event-driven replay pipeline) ───────────────

    @Test
    void onRecordingAvailable_registersArtifactStampsRefTransitionsAndEmits() {
        LiveEventEntity event = event(LiveEventStatus.ENDED.name(), true);
        LiveEventSessionEntity session = session("room-1", null);
        UUID documentObjectId = UUID.randomUUID();
        when(documentServiceClient.registerExternal(eq(tenantId), any()))
                .thenReturn(Map.of("success", true, "data", Map.of("objectId", documentObjectId.toString())));
        when(documentServiceClient.getSignedUrl(tenantId, documentObjectId.toString()))
                .thenReturn(Map.of("data", Map.of("url", "https://minio.local/signed/recording.mp4")));

        replayService.onRecordingAvailable(event, session, "rtc-gateway",
                new ReplayService.RecordingAvailable(
                        "egress-9", "impilo-recordings", "rtc/sessions/room-1/egress-9.mp4", 1800L, 52_000_000L));

        // artifact registered with document-service using the recording's storage location
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> register = ArgumentCaptor.forClass(Map.class);
        verify(documentServiceClient).registerExternal(eq(tenantId), register.capture());
        assertThat(register.getValue())
                .containsEntry("bucket", "impilo-recordings")
                .containsEntry("key", "rtc/sessions/room-1/egress-9.mp4")
                .containsEntry("contentType", "video/mp4")
                .containsEntry("ownerService", "LIVE")
                .containsEntry("sizeBytes", 52_000_000L);

        // returned objectId stamped on live_event_sessions.recording_ref
        ArgumentCaptor<LiveEventSessionEntity> saved = ArgumentCaptor.forClass(LiveEventSessionEntity.class);
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().getRecordingRef()).isEqualTo(documentObjectId.toString());
        assertThat(saved.getValue().getPlaybackUrlRef()).isEqualTo("https://minio.local/signed/recording.mp4");

        // state machine driven ENDED -> PROCESSING_REPLAY -> PUBLISHED_REPLAY with enriched emit
        verify(eventService).beginReplayProcessing(tenantId, eventId, "rtc-gateway");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> publishPayload = ArgumentCaptor.forClass(Map.class);
        verify(eventService).publishReplay(eq(tenantId), eq(eventId), eq("rtc-gateway"), publishPayload.capture());
        assertThat(publishPayload.getValue())
                .containsEntry("documentObjectId", documentObjectId.toString())
                .containsEntry("durationSeconds", 1800L)
                .containsEntry("egressId", "egress-9");
    }

    @Test
    void onRecordingAvailable_alreadyRegistered_isIdempotentNoOp() {
        LiveEventEntity event = event(LiveEventStatus.PUBLISHED_REPLAY.name(), true);
        LiveEventSessionEntity session = session("room-1", UUID.randomUUID().toString());

        replayService.onRecordingAvailable(event, session, "rtc-gateway",
                new ReplayService.RecordingAvailable(
                        "egress-9", "impilo-recordings", "rtc/sessions/room-1/egress-9.mp4", 1800L, null));

        verifyNoInteractions(documentServiceClient);
        verify(sessionRepository, never()).save(any());
        verify(eventService, never()).beginReplayProcessing(any(), any(), anyString());
        verify(eventService, never()).publishReplay(any(), any(), anyString(), any());
    }

    @Test
    void onRecordingAvailable_replacesLegacyFabricatedRefWithRealArtifact() {
        LiveEventEntity event = event(LiveEventStatus.PROCESSING_REPLAY.name(), true);
        LiveEventSessionEntity session = session("room-1", "auto-recording-" + UUID.randomUUID());
        UUID documentObjectId = UUID.randomUUID();
        when(documentServiceClient.registerExternal(eq(tenantId), any()))
                .thenReturn(Map.of("data", Map.of("objectId", documentObjectId.toString())));
        when(documentServiceClient.getSignedUrl(tenantId, documentObjectId.toString()))
                .thenReturn(Map.of("data", Map.of("url", "https://minio.local/signed/recording.mp4")));

        replayService.onRecordingAvailable(event, session, "rtc-gateway",
                new ReplayService.RecordingAvailable(
                        "egress-9", "impilo-recordings", "rtc/sessions/room-1/egress-9.mp4", null, null));

        assertThat(session.getRecordingRef()).isEqualTo(documentObjectId.toString());
        // already PROCESSING_REPLAY: no second beginReplayProcessing, straight to publish
        verify(eventService, never()).beginReplayProcessing(any(), any(), anyString());
        verify(eventService).publishReplay(eq(tenantId), eq(eventId), eq("rtc-gateway"), any());
    }

    @Test
    void onRecordingAvailable_registrationFailure_leavesStateUntouchedForRetry() {
        LiveEventEntity event = event(LiveEventStatus.ENDED.name(), true);
        LiveEventSessionEntity session = session("room-1", null);
        when(documentServiceClient.registerExternal(eq(tenantId), any())).thenReturn(Map.of());

        replayService.onRecordingAvailable(event, session, "rtc-gateway",
                new ReplayService.RecordingAvailable(
                        "egress-9", "impilo-recordings", "rtc/sessions/room-1/egress-9.mp4", 1800L, null));

        assertThat(session.getRecordingRef()).isNull();
        verify(sessionRepository, never()).save(any());
        verify(eventService, never()).beginReplayProcessing(any(), any(), anyString());
        verify(eventService, never()).publishReplay(any(), any(), anyString(), any());
    }

    @Test
    void onRecordingAvailable_replayNotAllowed_doesNotAdoptArtifact() {
        LiveEventEntity event = event(LiveEventStatus.ENDED.name(), false);
        LiveEventSessionEntity session = session("room-1", null);

        replayService.onRecordingAvailable(event, session, "rtc-gateway",
                new ReplayService.RecordingAvailable(
                        "egress-9", "impilo-recordings", "rtc/sessions/room-1/egress-9.mp4", 1800L, null));

        verifyNoInteractions(documentServiceClient);
        verify(sessionRepository, never()).save(any());
    }

    private LiveEventEntity event(String status, boolean replayAllowed) {
        LiveEventEntity event = new LiveEventEntity();
        event.setId(eventId);
        event.setTenantId(tenantId);
        event.setStatus(status);
        event.setReplayAllowed(replayAllowed);
        event.setRecordingAllowed(true);
        event.setTitle("Test");
        event.setEventType("WEBINAR");
        event.setContextType("PROFESSIONAL");
        event.setAudienceType("PROVIDERS");
        event.setVisibility("INTERNAL");
        event.setOrganiserType("PROVIDER");
        event.setOrganiserId("org-1");
        event.setCreatedBy("org-1");
        return event;
    }

    private LiveEventSessionEntity session(String roomId, String recordingRef) {
        LiveEventSessionEntity session = new LiveEventSessionEntity();
        session.setId(UUID.randomUUID());
        session.setEventId(eventId);
        session.setProviderRoomId(roomId);
        session.setRecordingRef(recordingRef);
        session.setProviderType("LOCAL_DEV");
        return session;
    }
}
