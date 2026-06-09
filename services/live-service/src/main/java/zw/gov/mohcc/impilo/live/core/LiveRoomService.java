package zw.gov.mohcc.impilo.live.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.live.media.LiveMediaProvider;
import zw.gov.mohcc.impilo.live.media.MediaRoomContext;
import zw.gov.mohcc.impilo.live.media.MediaTokenResult;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventSessionEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventSessionRepository;

import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LiveRoomService {

    private final LiveEventService eventService;
    private final AttendanceService attendanceService;
    private final LiveEventSessionRepository sessionRepository;
    private final LiveMediaProvider mediaProvider;
    private final ReplayService replayService;
    private final LiveGovernanceGuard governanceGuard;

    public LiveRoomService(LiveEventService eventService,
                           AttendanceService attendanceService,
                           LiveEventSessionRepository sessionRepository,
                           LiveMediaProvider mediaProvider,
                           ReplayService replayService,
                           LiveGovernanceGuard governanceGuard) {
        this.eventService = eventService;
        this.attendanceService = attendanceService;
        this.sessionRepository = sessionRepository;
        this.mediaProvider = mediaProvider;
        this.replayService = replayService;
        this.governanceGuard = governanceGuard;
    }

    @Transactional
    public LiveEventSessionEntity join(UUID tenantId, UUID eventId,
                                       String participantId, String participantType,
                                       String role, boolean consentGranted) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        governanceGuard.validateJoin(event, role, participantType, consentGranted);
        attendanceService.join(tenantId, eventId, participantId, participantType);
        LiveEventSessionEntity session = sessionRepository
                .findFirstByEventIdAndEndedAtIsNullOrderByCreatedAtDesc(eventId)
                .orElseGet(() -> createSession(event));
        MediaRoomContext ctx = new MediaRoomContext(
                tenantId, eventId, session.getId(), participantId, role,
                event.getFacilityId(), session.getProviderRoomId(),
                mediaProvider.providerType(), Map.of());
        if (session.getProviderRoomId() == null) {
            MediaRoomContext provisioned = mediaProvider.provisionRoom(ctx);
            session.setProviderRoomId(provisioned.providerRoomId());
            session.setProviderType(provisioned.providerType());
            sessionRepository.save(session);
        }
        return session;
    }

    @Transactional(readOnly = true)
    public MediaTokenResult token(UUID tenantId, UUID eventId, String participantId, String role) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        LiveEventSessionEntity session = sessionRepository
                .findFirstByEventIdAndEndedAtIsNullOrderByCreatedAtDesc(eventId)
                .orElseThrow(() -> new IllegalStateException("No active session — join first"));
        MediaRoomContext ctx = new MediaRoomContext(
                tenantId, eventId, session.getId(), participantId, role,
                event.getFacilityId(), session.getProviderRoomId(),
                session.getProviderType(), Map.of());
        return mediaProvider.issueToken(ctx);
    }

    @Transactional
    public LiveEventSessionEntity start(UUID tenantId, UUID eventId, String updatedBy) {
        LiveEventEntity event = eventService.goLive(tenantId, eventId, updatedBy);
        LiveEventSessionEntity session = sessionRepository
                .findFirstByEventIdAndEndedAtIsNullOrderByCreatedAtDesc(eventId)
                .orElseGet(() -> createSession(event));
        session.setStartedAt(OffsetDateTime.now());
        session.setHealthStatus("HEALTHY");
        if (session.getProviderRoomId() != null) {
            mediaProvider.startSession(session.getProviderRoomId());
        }
        return sessionRepository.save(session);
    }

    @Transactional
    public LiveEventSessionEntity end(UUID tenantId, UUID eventId, String updatedBy) {
        eventService.end(tenantId, eventId, updatedBy);
        LiveEventSessionEntity session = sessionRepository
                .findFirstByEventIdAndEndedAtIsNullOrderByCreatedAtDesc(eventId)
                .orElseThrow(() -> new IllegalStateException("No active session"));
        session.setEndedAt(OffsetDateTime.now());
        session.setHealthStatus("ENDED");
        if (session.getProviderRoomId() != null) {
            mediaProvider.endSession(session.getProviderRoomId());
        }
        LiveEventSessionEntity saved = sessionRepository.save(session);
        replayService.onSessionEnd(tenantId, eventId, updatedBy, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> mediaHealth(UUID tenantId, UUID eventId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("providerType", mediaProvider.providerType());
        health.put("productionReady", !"LOCAL_DEV".equals(mediaProvider.providerType()));
        health.put("eventStatus", event.getStatus());
        health.put("recordingAllowed", event.isRecordingAllowed());
        health.put("replayAllowed", event.isReplayAllowed());
        sessionRepository.findFirstByEventIdAndEndedAtIsNullOrderByCreatedAtDesc(eventId)
                .or(() -> sessionRepository.findByEventIdOrderByCreatedAtDesc(eventId).stream().findFirst())
                .ifPresentOrElse(session -> {
                    health.put("sessionId", session.getId().toString());
                    health.put("healthStatus", session.getHealthStatus());
                    health.put("providerRoomId", session.getProviderRoomId());
                    if (session.getProviderRoomId() != null) {
                        var status = mediaProvider.checkHealth(session.getProviderRoomId());
                        health.put("mediaHealth", status.status());
                        health.put("mediaHealthy", status.healthy());
                        health.put("mediaNote", status.message());
                    }
                }, () -> health.put("healthStatus", "NO_SESSION"));
        return health;
    }

    @Transactional
    public LiveEventSessionEntity startRecording(UUID tenantId, UUID eventId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        governanceGuard.validateRecordingRequest(event);
        LiveEventSessionEntity session = sessionRepository
                .findFirstByEventIdAndEndedAtIsNullOrderByCreatedAtDesc(eventId)
                .orElseThrow(() -> new IllegalStateException("No active session"));
        String ref = mediaProvider.startRecording(session.getProviderRoomId());
        session.setRecordingRef(ref);
        return sessionRepository.save(session);
    }

    private LiveEventSessionEntity createSession(LiveEventEntity event) {
        LiveEventSessionEntity session = new LiveEventSessionEntity();
        session.setEventId(event.getId());
        session.setProviderType(mediaProvider.providerType());
        return sessionRepository.save(session);
    }
}
