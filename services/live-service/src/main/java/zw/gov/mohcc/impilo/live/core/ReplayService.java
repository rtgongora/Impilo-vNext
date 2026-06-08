package zw.gov.mohcc.impilo.live.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.live.domain.LiveEventStatus;
import zw.gov.mohcc.impilo.live.media.LiveMediaProvider;
import zw.gov.mohcc.impilo.live.media.MediaRoomContext;
import zw.gov.mohcc.impilo.live.media.MediaTokenResult;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventSessionEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventSessionRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ReplayService {

    private final LiveEventService eventService;
    private final LiveEventSessionRepository sessionRepository;
    private final LiveMediaProvider mediaProvider;

    public ReplayService(LiveEventService eventService,
                         LiveEventSessionRepository sessionRepository,
                         LiveMediaProvider mediaProvider) {
        this.eventService = eventService;
        this.sessionRepository = sessionRepository;
        this.mediaProvider = mediaProvider;
    }

    @Transactional
    public void onSessionEnd(UUID tenantId, UUID eventId, String updatedBy, LiveEventSessionEntity session) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        if (!event.isReplayAllowed()) {
            return;
        }
        finalizeRecording(session);
        sessionRepository.save(session);
        if (LiveEventStatus.ENDED.name().equals(event.getStatus())) {
            processReplay(tenantId, eventId, updatedBy);
        }
    }

    @Transactional
    public Map<String, Object> processReplay(UUID tenantId, UUID eventId, String updatedBy) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        LiveEventSessionEntity session = latestSession(eventId);
        finalizeRecording(session);
        sessionRepository.save(session);

        if (LiveEventStatus.ENDED.name().equals(event.getStatus())) {
            eventService.beginReplayProcessing(tenantId, eventId, updatedBy);
        }
        if (session.getPlaybackUrlRef() != null && !session.getPlaybackUrlRef().isBlank()) {
            event = eventService.get(tenantId, eventId);
            if (LiveEventStatus.PROCESSING_REPLAY.name().equals(event.getStatus())) {
                eventService.publishReplay(tenantId, eventId, updatedBy);
            }
        }
        return replayMap(eventService.get(tenantId, eventId), session);
    }

    @Transactional
    public Map<String, Object> publishReplay(UUID tenantId, UUID eventId, String updatedBy) {
        LiveEventSessionEntity session = latestSession(eventId);
        if (session.getPlaybackUrlRef() == null || session.getPlaybackUrlRef().isBlank()) {
            throw new IllegalStateException("Playback URL not ready — run process-replay first");
        }
        LiveEventEntity event = eventService.publishReplay(tenantId, eventId, updatedBy);
        return replayMap(event, session);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getReplay(UUID tenantId, UUID eventId, String participantId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        LiveEventSessionEntity session = latestSession(eventId);
        Map<String, Object> map = replayMap(event, session);
        if (LiveEventStatus.PUBLISHED_REPLAY.name().equals(event.getStatus())
                && session.getPlaybackUrlRef() != null
                && isLiveKitPlayback(session.getPlaybackUrlRef())
                && session.getProviderRoomId() != null) {
            MediaRoomContext ctx = new MediaRoomContext(
                    tenantId, eventId, session.getId(), participantId, "ATTENDEE",
                    event.getFacilityId(), session.getProviderRoomId(),
                    session.getProviderType(), Map.of("mode", "REPLAY"));
            MediaTokenResult token = mediaProvider.issueToken(ctx);
            map.put("replayRoomUrl", token.roomUrl());
            map.put("replayAccessToken", token.accessToken());
        }
        return map;
    }

    private void finalizeRecording(LiveEventSessionEntity session) {
        String roomId = session.getProviderRoomId();
        if (roomId == null) {
            return;
        }
        String recordingRef = session.getRecordingRef();
        if (recordingRef == null) {
            recordingRef = "auto-recording-" + session.getId();
            session.setRecordingRef(recordingRef);
        }
        mediaProvider.stopRecording(roomId, recordingRef);
        session.setPlaybackUrlRef(mediaProvider.getPlaybackUrl(roomId, recordingRef));
    }

    private LiveEventSessionEntity latestSession(UUID eventId) {
        return sessionRepository.findByEventIdOrderByCreatedAtDesc(eventId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No session for event: " + eventId));
    }

    private static boolean isLiveKitPlayback(String url) {
        return url.startsWith("ws://") || url.startsWith("wss://");
    }

    private Map<String, Object> replayMap(LiveEventEntity event, LiveEventSessionEntity session) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventId", event.getId().toString());
        map.put("status", event.getStatus());
        map.put("replayAllowed", event.isReplayAllowed());
        map.put("recordingAllowed", event.isRecordingAllowed());
        map.put("sessionId", session.getId().toString());
        map.put("recordingRef", session.getRecordingRef());
        map.put("playbackUrl", session.getPlaybackUrlRef());
        map.put("providerType", session.getProviderType());
        return map;
    }
}
