package zw.gov.mohcc.impilo.live.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.live.media.LiveMediaProvider;
import zw.gov.mohcc.impilo.live.media.MediaRoomContext;
import zw.gov.mohcc.impilo.live.media.MediaTokenResult;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventSessionEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventSessionRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LiveRoomService {

    /** Attribute keys understood by the media provider (rtc-gateway seam). */
    static final String ATTR_SESSION_MODE = "sessionMode";
    static final String ATTR_MAX_PARTICIPANTS = "maxParticipants";
    static final String ATTR_SESSION_ID_SUFFIX = "sessionIdSuffix";
    static final String ATTR_PARENT_SESSION_ID = "parentSessionId";
    static final String BACKSTAGE_SUFFIX = "-backstage";

    private final LiveEventService eventService;
    private final AttendanceService attendanceService;
    private final LiveEventSessionRepository sessionRepository;
    private final LiveMediaProvider mediaProvider;
    private final ReplayService replayService;
    private final LiveGovernanceGuard governanceGuard;
    private final StageService stageService;

    public LiveRoomService(LiveEventService eventService,
                           AttendanceService attendanceService,
                           LiveEventSessionRepository sessionRepository,
                           LiveMediaProvider mediaProvider,
                           ReplayService replayService,
                           LiveGovernanceGuard governanceGuard,
                           StageService stageService) {
        this.eventService = eventService;
        this.attendanceService = attendanceService;
        this.sessionRepository = sessionRepository;
        this.mediaProvider = mediaProvider;
        this.replayService = replayService;
        this.governanceGuard = governanceGuard;
        this.stageService = stageService;
    }

    /** A minted media token together with the server-resolved role it grants. */
    public record RoomToken(MediaTokenResult token, String role) {}

    @Transactional
    public LiveEventSessionEntity join(UUID tenantId, UUID eventId,
                                       String participantId, String participantType,
                                       String role, boolean consentGranted) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        // Stage-managed (LIVE_EVENT) modes resolve the role server-side; a
        // client-asserted SPEAKER without a grant joins as AUDIENCE.
        String effectiveRole = stageService.resolveEffectiveRole(event, participantId, role);
        governanceGuard.validateJoin(event, effectiveRole, participantType, consentGranted);
        attendanceService.join(tenantId, eventId, participantId, participantType);
        LiveEventSessionEntity session = sessionRepository
                .findFirstByEventIdAndEndedAtIsNullOrderByCreatedAtDesc(eventId)
                .orElseGet(() -> createSession(event));
        if (session.getProviderRoomId() == null) {
            provisionMainRoom(event, session, participantId, effectiveRole);
        }
        return session;
    }

    @Transactional(readOnly = true)
    public RoomToken token(UUID tenantId, UUID eventId, String participantId, String role) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        String effectiveRole = stageService.resolveEffectiveRole(event, participantId, role);
        LiveEventSessionEntity session = sessionRepository
                .findFirstByEventIdAndEndedAtIsNullOrderByCreatedAtDesc(eventId)
                .orElseThrow(() -> new IllegalStateException("No active session — join first"));
        MediaRoomContext ctx = new MediaRoomContext(
                tenantId, eventId, session.getId(), participantId, effectiveRole,
                event.getFacilityId(), session.getProviderRoomId(),
                session.getProviderType(), roomAttributes(event));
        return new RoomToken(mediaProvider.issueToken(ctx), effectiveRole);
    }

    // ── Backstage (linked child room for pre-stage speakers) ─────────

    /**
     * Ensure the backstage child room exists and return the session. Backstage
     * access needs a stage grant (crew or approved speaker) — the audience
     * never enters backstage.
     */
    @Transactional
    public LiveEventSessionEntity joinBackstage(UUID tenantId, UUID eventId,
                                                String participantId, String participantType) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        String effectiveRole = requireStageGrant(event, participantId);
        governanceGuard.validateJoin(event, effectiveRole, participantType, false);
        LiveEventSessionEntity session = sessionRepository
                .findFirstByEventIdAndEndedAtIsNullOrderByCreatedAtDesc(eventId)
                .orElseGet(() -> createSession(event));
        if (session.getProviderRoomId() == null) {
            // The backstage room parent-links to the main room, so the main
            // room must exist first (idempotent to provision it now).
            provisionMainRoom(event, session, participantId, effectiveRole);
        }
        if (session.getBackstageRoomId() == null) {
            Map<String, Object> attributes = roomAttributes(event);
            attributes.put(ATTR_SESSION_ID_SUFFIX, BACKSTAGE_SUFFIX);
            attributes.put(ATTR_PARENT_SESSION_ID, session.getProviderRoomId());
            MediaRoomContext ctx = new MediaRoomContext(
                    tenantId, eventId, session.getId(), participantId, effectiveRole,
                    event.getFacilityId(), null, mediaProvider.providerType(), attributes);
            MediaRoomContext provisioned = mediaProvider.provisionRoom(ctx);
            session.setBackstageRoomId(provisioned.providerRoomId());
            sessionRepository.save(session);
        }
        return session;
    }

    /** Mint a token for the backstage room (stage grant required). */
    @Transactional(readOnly = true)
    public RoomToken backstageToken(UUID tenantId, UUID eventId, String participantId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        String effectiveRole = requireStageGrant(event, participantId);
        LiveEventSessionEntity session = sessionRepository
                .findFirstByEventIdAndEndedAtIsNullOrderByCreatedAtDesc(eventId)
                .orElseThrow(() -> new IllegalStateException("No active session — join backstage first"));
        if (session.getBackstageRoomId() == null) {
            throw new IllegalStateException("No backstage room provisioned — join backstage first");
        }
        MediaRoomContext ctx = new MediaRoomContext(
                tenantId, eventId, session.getId(), participantId, effectiveRole,
                event.getFacilityId(), session.getBackstageRoomId(),
                session.getProviderType(), roomAttributes(event));
        return new RoomToken(mediaProvider.issueToken(ctx), effectiveRole);
    }

    private String requireStageGrant(LiveEventEntity event, String participantId) {
        StageService.StageTier tier = stageService.resolveTier(event, participantId);
        if (tier == StageService.StageTier.AUDIENCE) {
            throw LiveGovernanceException.forbidden(
                    "BACKSTAGE_STAGE_GRANT_REQUIRED",
                    "Backstage is for the crew and approved speakers only.");
        }
        return tier.name();
    }

    private void provisionMainRoom(LiveEventEntity event, LiveEventSessionEntity session,
                                   String participantId, String role) {
        MediaRoomContext ctx = new MediaRoomContext(
                event.getTenantId(), event.getId(), session.getId(), participantId, role,
                event.getFacilityId(), session.getProviderRoomId(),
                mediaProvider.providerType(), roomAttributes(event));
        MediaRoomContext provisioned = mediaProvider.provisionRoom(ctx);
        session.setProviderRoomId(provisioned.providerRoomId());
        session.setProviderType(provisioned.providerType());
        sessionRepository.save(session);
    }

    /**
     * Carry the event's platform SessionMode (session-template taxonomy) and
     * capacity to the media provider: the room is provisioned against the
     * right grant taxonomy and, when the event caps registrations, LiveKit
     * enforces that cap as the room's max participants.
     */
    private Map<String, Object> roomAttributes(LiveEventEntity event) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        zw.gov.mohcc.impilo.live.domain.LiveMode mode;
        try {
            mode = zw.gov.mohcc.impilo.live.domain.LiveMode.fromString(event.getMode());
        } catch (IllegalArgumentException e) {
            mode = null;
        }
        if (mode != null) {
            attributes.put(ATTR_SESSION_MODE, mode.sessionMode());
        }
        if (event.getMaxParticipants() != null && event.getMaxParticipants() > 0) {
            attributes.put(ATTR_MAX_PARTICIPANTS, event.getMaxParticipants());
        }
        return attributes;
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
        if (session.getBackstageRoomId() != null) {
            mediaProvider.endSession(session.getBackstageRoomId());
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
                    health.put("backstageRoomId", session.getBackstageRoomId());
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
