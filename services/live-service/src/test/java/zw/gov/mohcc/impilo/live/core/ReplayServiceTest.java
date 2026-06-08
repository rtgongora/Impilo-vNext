package zw.gov.mohcc.impilo.live.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.live.domain.LiveEventStatus;
import zw.gov.mohcc.impilo.live.media.LiveMediaProvider;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventSessionEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventSessionRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplayServiceTest {

    @Mock
    private LiveEventService eventService;
    @Mock
    private LiveEventSessionRepository sessionRepository;
    @Mock
    private LiveMediaProvider mediaProvider;

    private ReplayService replayService;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        replayService = new ReplayService(eventService, sessionRepository, mediaProvider);
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
