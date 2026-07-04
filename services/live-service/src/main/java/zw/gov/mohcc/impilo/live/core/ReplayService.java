package zw.gov.mohcc.impilo.live.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.live.domain.LiveEventStatus;
import zw.gov.mohcc.impilo.live.integration.DocumentServiceClient;
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

    private static final Logger log = LoggerFactory.getLogger(ReplayService.class);

    private final LiveEventService eventService;
    private final LiveEventSessionRepository sessionRepository;
    private final LiveMediaProvider mediaProvider;
    private final DocumentServiceClient documentServiceClient;

    public ReplayService(LiveEventService eventService,
                         LiveEventSessionRepository sessionRepository,
                         LiveMediaProvider mediaProvider,
                         DocumentServiceClient documentServiceClient) {
        this.eventService = eventService;
        this.sessionRepository = sessionRepository;
        this.mediaProvider = mediaProvider;
        this.documentServiceClient = documentServiceClient;
    }

    /** Media-truth payload of {@code impilo.rtc.recording.available.v1} relevant to replay wiring. */
    public record RecordingAvailable(String egressId, String storageBucket, String storageKey,
                                     Long durationSeconds, Long sizeBytes) {}

    /**
     * Event-driven replay pipeline: an rtc-gateway recording egress finished and its
     * artifact landed in MinIO. Adopt the artifact into the document catalogue
     * (register-external — the artifact SoR), stamp the returned document object id
     * on {@code live_event_sessions.recording_ref}, and drive the event through
     * ENDED → PROCESSING_REPLAY → PUBLISHED_REPLAY via the existing transitions,
     * emitting {@code impilo.live.replay.published.v1} enriched with the artifact refs.
     *
     * <p>Idempotency: a recording_ref that is already a document object id (UUID)
     * means the artifact was registered — redeliveries are no-ops. Legacy fabricated
     * refs ("auto-recording-…", "rtc-recording-…") are not document objects and get
     * replaced by the real artifact reference.
     */
    @Transactional
    public void onRecordingAvailable(LiveEventEntity event, LiveEventSessionEntity session,
                                     String updatedBy, RecordingAvailable recording) {
        if (isDocumentObjectRef(session.getRecordingRef())) {
            log.debug("Recording for session {} already registered as document object {} — no-op",
                    session.getId(), session.getRecordingRef());
            return;
        }
        if (!event.isReplayAllowed()) {
            log.info("Recording available for event {} but replay is not allowed — artifact not adopted",
                    event.getId());
            return;
        }
        if (recording.storageBucket() == null || recording.storageKey() == null) {
            log.warn("Recording available for session {} without storage location (bucket={}, key={}) — skipped",
                    session.getId(), recording.storageBucket(), recording.storageKey());
            return;
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("bucket", recording.storageBucket());
        request.put("key", recording.storageKey());
        request.put("contentType", "video/mp4");
        if (recording.sizeBytes() != null) {
            request.put("sizeBytes", recording.sizeBytes());
        }
        request.put("ownerService", "LIVE");
        request.put("title", (event.getTitle() != null ? event.getTitle() : "Live event")
                + " — replay recording");
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("liveEventId", event.getId().toString());
        tags.put("liveSessionId", session.getId().toString());
        if (recording.egressId() != null) {
            tags.put("egressId", recording.egressId());
        }
        if (recording.durationSeconds() != null) {
            tags.put("durationSeconds", recording.durationSeconds().toString());
        }
        request.put("tags", tags);

        Map<String, Object> data = unwrapData(documentServiceClient.registerExternal(event.getTenantId(), request));
        String documentObjectId = strOrNull(data.get("objectId"));
        if (documentObjectId == null) {
            log.warn("document-service register-external returned no objectId for session {} — replay left untouched",
                    session.getId());
            return;
        }

        session.setRecordingRef(documentObjectId);
        Map<String, Object> signed = unwrapData(documentServiceClient.getSignedUrl(event.getTenantId(), documentObjectId));
        String playbackUrl = strOrNull(signed.get("url"));
        if (playbackUrl != null) {
            session.setPlaybackUrlRef(playbackUrl);
        }
        sessionRepository.save(session);

        Map<String, Object> replayPayload = new LinkedHashMap<>();
        replayPayload.put("documentObjectId", documentObjectId);
        replayPayload.put("sessionId", session.getId().toString());
        if (recording.durationSeconds() != null) {
            replayPayload.put("durationSeconds", recording.durationSeconds());
        }
        if (recording.egressId() != null) {
            replayPayload.put("egressId", recording.egressId());
        }

        String status = event.getStatus();
        if (LiveEventStatus.ENDED.name().equals(status)) {
            // Direct ENDED -> PUBLISHED_REPLAY is illegal; pass through PROCESSING_REPLAY.
            eventService.beginReplayProcessing(event.getTenantId(), event.getId(), updatedBy);
            eventService.publishReplay(event.getTenantId(), event.getId(), updatedBy, replayPayload);
            log.info("Replay published for event {} from recording artifact {} (egress={})",
                    event.getId(), documentObjectId, recording.egressId());
        } else if (LiveEventStatus.PROCESSING_REPLAY.name().equals(status)) {
            eventService.publishReplay(event.getTenantId(), event.getId(), updatedBy, replayPayload);
            log.info("Replay published for event {} (was already processing) from artifact {}",
                    event.getId(), documentObjectId);
        } else {
            log.info("Recording artifact {} registered for event {} in status {} — replay publish not triggered",
                    documentObjectId, event.getId(), status);
        }
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

    /** A recording_ref that parses as a UUID is a catalogued document object id. */
    private static boolean isDocumentObjectRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(ref.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapData(Map<String, Object> response) {
        return response.get("data") instanceof Map<?, ?> raw ? (Map<String, Object>) raw : response;
    }

    private static String strOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString();
        return s.isBlank() ? null : s;
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
