package zw.gov.mohcc.impilo.live.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.live.domain.LiveEventStatus;
import zw.gov.mohcc.impilo.live.domain.LiveMode;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventRoleAssignmentEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventStageRequestEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventRoleAssignmentRepository;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventStageRequestRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * LIVE_EVENT stage management: audience→stage promotion requests, the crew
 * decision machine (approve / deny / demote), and — critically — server-side
 * resolution of the effective room role.
 *
 * <p>Role escalation is never client-asserted: the tier a participant may hold
 * comes from the event's role assignments (organiser/creator ⇒ HOST) plus an
 * APPROVED stage request (⇒ SPEAKER). A requested role above the resolved tier
 * is clamped down to the tier, so an unapproved participant asking for SPEAKER
 * receives an AUDIENCE (subscribe-only) grant.
 */
@Service
public class StageService {

    /** Publish/admin tier order, highest first. */
    public enum StageTier {
        HOST, PRODUCER, MODERATOR, SPEAKER, AUDIENCE;

        boolean atLeast(StageTier other) {
            return ordinal() <= other.ordinal();
        }
    }

    static final String EVT_STAGE_REQUESTED = "impilo.live.stage.requested.v1";
    static final String EVT_STAGE_APPROVED = "impilo.live.stage.approved.v1";
    static final String EVT_STAGE_DEMOTED = "impilo.live.stage.demoted.v1";

    private static final Logger log = LoggerFactory.getLogger(StageService.class);

    private static final List<String> OPEN_STATUSES = List.of(
            LiveEventStageRequestEntity.STATUS_REQUESTED, LiveEventStageRequestEntity.STATUS_APPROVED);

    private final LiveEventService eventService;
    private final LiveEventStageRequestRepository stageRequests;
    private final LiveEventRoleAssignmentRepository roleAssignments;
    private final LiveEventEmitter emitter;

    public StageService(LiveEventService eventService,
                        LiveEventStageRequestRepository stageRequests,
                        LiveEventRoleAssignmentRepository roleAssignments,
                        LiveEventEmitter emitter) {
        this.eventService = eventService;
        this.stageRequests = stageRequests;
        this.roleAssignments = roleAssignments;
        this.emitter = emitter;
    }

    // ── Request lifecycle ────────────────────────────────────────────

    /** Idempotent: an existing open (REQUESTED/APPROVED) request is returned as-is. */
    @Transactional
    public LiveEventStageRequestEntity requestStage(UUID tenantId, UUID eventId,
                                                    String participantId, String participantType) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        assertStageManagedMode(event);
        String status = event.getStatus();
        if (!LiveEventStatus.LIVE.name().equals(status) && !LiveEventStatus.SCHEDULED.name().equals(status)) {
            throw new IllegalStateException("Stage requests are only open while the event is scheduled or live");
        }
        LiveEventStageRequestEntity existing = stageRequests
                .findFirstByEventIdAndParticipantIdAndStatusInOrderByRequestedAtDesc(
                        eventId, participantId, OPEN_STATUSES)
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        LiveEventStageRequestEntity request = new LiveEventStageRequestEntity();
        request.setEventId(eventId);
        request.setParticipantId(participantId);
        request.setParticipantType(participantType != null ? participantType : "CITIZEN");
        request.setStatus(LiveEventStageRequestEntity.STATUS_REQUESTED);
        LiveEventStageRequestEntity saved = stageRequests.save(request);
        emitStageEvent(event, saved, EVT_STAGE_REQUESTED);
        return saved;
    }

    /** Crew (HOST/PRODUCER) approves; the participant's tier becomes SPEAKER. Idempotent on APPROVED. */
    @Transactional
    public LiveEventStageRequestEntity approve(UUID tenantId, UUID eventId, UUID requestId, String actorId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        assertCrew(event, actorId);
        LiveEventStageRequestEntity request = requireRequest(eventId, requestId);
        if (LiveEventStageRequestEntity.STATUS_APPROVED.equals(request.getStatus())) {
            return request;
        }
        if (!LiveEventStageRequestEntity.STATUS_REQUESTED.equals(request.getStatus())) {
            throw new IllegalStateException("Only a REQUESTED stage request can be approved (was "
                    + request.getStatus() + ")");
        }
        request.setStatus(LiveEventStageRequestEntity.STATUS_APPROVED);
        request.setDecidedBy(actorId);
        request.setDecidedAt(OffsetDateTime.now());
        LiveEventStageRequestEntity saved = stageRequests.save(request);
        emitStageEvent(event, saved, EVT_STAGE_APPROVED);
        return saved;
    }

    /** Crew denies a pending request. Idempotent on DENIED. */
    @Transactional
    public LiveEventStageRequestEntity deny(UUID tenantId, UUID eventId, UUID requestId, String actorId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        assertCrew(event, actorId);
        LiveEventStageRequestEntity request = requireRequest(eventId, requestId);
        if (LiveEventStageRequestEntity.STATUS_DENIED.equals(request.getStatus())) {
            return request;
        }
        if (!LiveEventStageRequestEntity.STATUS_REQUESTED.equals(request.getStatus())) {
            throw new IllegalStateException("Only a REQUESTED stage request can be denied (was "
                    + request.getStatus() + ")");
        }
        request.setStatus(LiveEventStageRequestEntity.STATUS_DENIED);
        request.setDecidedBy(actorId);
        request.setDecidedAt(OffsetDateTime.now());
        return stageRequests.save(request);
    }

    /**
     * Return a participant to AUDIENCE: demotes their APPROVED stage request
     * and removes any SPEAKER-tier role assignments. Subsequent token requests
     * resolve to AUDIENCE; the client refresh flow re-mints against that.
     */
    @Transactional
    public Map<String, Object> demoteParticipant(UUID tenantId, UUID eventId, String participantId, String actorId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        assertCrew(event, actorId);
        boolean changed = false;
        LiveEventStageRequestEntity approved = stageRequests
                .findFirstByEventIdAndParticipantIdAndStatusInOrderByRequestedAtDesc(
                        eventId, participantId, List.of(LiveEventStageRequestEntity.STATUS_APPROVED))
                .orElse(null);
        if (approved != null) {
            approved.setStatus(LiveEventStageRequestEntity.STATUS_DEMOTED);
            approved.setDecidedBy(actorId);
            approved.setDecidedAt(OffsetDateTime.now());
            stageRequests.save(approved);
            changed = true;
        }
        List<LiveEventRoleAssignmentEntity> assignments = roleAssignments.findByEventId(eventId).stream()
                .filter(a -> participantId.equals(a.getUserId()))
                .filter(a -> tierOf(a.getRole()) == StageTier.SPEAKER)
                .toList();
        if (!assignments.isEmpty()) {
            roleAssignments.deleteAll(assignments);
            changed = true;
        }
        if (!changed) {
            throw new IllegalArgumentException(
                    "Participant " + participantId + " holds no stage grant on this event");
        }
        emitStageEvent(event, approved, participantId, EVT_STAGE_DEMOTED, actorId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("eventId", eventId);
        out.put("participantId", participantId);
        out.put("tier", StageTier.AUDIENCE.name());
        out.put("demotedBy", actorId);
        return out;
    }

    @Transactional(readOnly = true)
    public List<LiveEventStageRequestEntity> list(UUID tenantId, UUID eventId, String status) {
        eventService.get(tenantId, eventId);
        if (status == null || status.isBlank()) {
            return stageRequests.findByEventIdOrderByRequestedAtAsc(eventId);
        }
        return stageRequests.findByEventIdAndStatusOrderByRequestedAtAsc(
                eventId, status.trim().toUpperCase(Locale.ROOT));
    }

    /** The participant's server-resolved tier + latest stage request — the web's role truth. */
    @Transactional(readOnly = true)
    public Map<String, Object> myStageRole(UUID tenantId, UUID eventId, String participantId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        StageTier tier = resolveTier(event, participantId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("eventId", eventId);
        out.put("participantId", participantId);
        out.put("tier", tier.name());
        out.put("stageManaged", isStageManagedMode(event));
        stageRequests.findFirstByEventIdAndParticipantIdOrderByRequestedAtDesc(eventId, participantId)
                .ifPresent(r -> out.put("stageRequest", requestView(r)));
        return out;
    }

    // ── Server-side role resolution ──────────────────────────────────

    /**
     * The effective template role for a token/join on a stage-managed
     * (LIVE_EVENT SessionMode) event. Non-stage-managed modes (meetings,
     * classrooms, clinical) keep the caller's role untouched.
     */
    @Transactional(readOnly = true)
    public String resolveEffectiveRole(LiveEventEntity event, String participantId, String requestedRole) {
        if (!isStageManagedMode(event)) {
            return requestedRole;
        }
        StageTier requested = tierOf(requestedRole);
        StageTier granted = resolveTier(event, participantId);
        boolean allowed = switch (granted) {
            // Crew may take any seat (produce, speak, moderate, or just watch).
            case HOST, PRODUCER -> true;
            // Moderators and speakers hold exactly their grant (or watch);
            // MODERATOR→SPEAKER would trade roomAdmin for publish — still an
            // escalation on the publish axis, so it is not honoured.
            case MODERATOR, SPEAKER -> requested == granted || requested == StageTier.AUDIENCE;
            case AUDIENCE -> requested == StageTier.AUDIENCE;
        };
        if (!allowed) {
            log.info("Participant {} requested role {} on event {} but holds tier {} — clamped",
                    participantId, requestedRole, event.getId(), granted);
            return granted.name();
        }
        return requested.name();
    }

    /** Highest tier the participant holds on this event. */
    @Transactional(readOnly = true)
    public StageTier resolveTier(LiveEventEntity event, String participantId) {
        if (participantId == null || participantId.isBlank()) {
            return StageTier.AUDIENCE;
        }
        if (participantId.equals(event.getOrganiserId()) || participantId.equals(event.getCreatedBy())) {
            return StageTier.HOST;
        }
        StageTier best = StageTier.AUDIENCE;
        for (LiveEventRoleAssignmentEntity assignment : roleAssignments.findByEventId(event.getId())) {
            if (!participantId.equals(assignment.getUserId())) {
                continue;
            }
            StageTier tier = tierOf(assignment.getRole());
            if (tier.atLeast(best)) {
                best = tier;
            }
        }
        if (best == StageTier.AUDIENCE) {
            boolean approved = stageRequests
                    .findFirstByEventIdAndParticipantIdAndStatusInOrderByRequestedAtDesc(
                            event.getId(), participantId,
                            List.of(LiveEventStageRequestEntity.STATUS_APPROVED))
                    .isPresent();
            if (approved) {
                best = StageTier.SPEAKER;
            }
        }
        return best;
    }

    /** Crew = organiser/creator or an assigned HOST/PRODUCER — the stage decision authority. */
    public void assertCrew(LiveEventEntity event, String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw LiveGovernanceException.forbidden(
                    "STAGE_ACTOR_REQUIRED", "Stage decisions require an authenticated actor (X-Actor-ID).");
        }
        StageTier tier = resolveTier(event, actorId);
        if (tier != StageTier.HOST && tier != StageTier.PRODUCER) {
            throw LiveGovernanceException.forbidden(
                    "STAGE_CREW_REQUIRED",
                    "Only the event host or producer may manage the stage.");
        }
    }

    /** Legacy + template role vocabulary → tier. Unknown roles are audience (fail-closed). */
    public static StageTier tierOf(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HOST", "COHOST", "ADMIN" -> StageTier.HOST;
            case "PRODUCER" -> StageTier.PRODUCER;
            case "MODERATOR", "SUPPORT", "CPD_VERIFIER" -> StageTier.MODERATOR;
            case "SPEAKER", "PRESENTER", "INTERPRETER" -> StageTier.SPEAKER;
            default -> StageTier.AUDIENCE;
        };
    }

    /** Stage management applies to modes riding the LIVE_EVENT session template. */
    public static boolean isStageManagedMode(LiveEventEntity event) {
        LiveMode mode;
        try {
            mode = LiveMode.fromString(event.getMode());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return mode != null && "LIVE_EVENT".equals(mode.sessionMode());
    }

    // ── internals ────────────────────────────────────────────────────

    private void assertStageManagedMode(LiveEventEntity event) {
        if (!isStageManagedMode(event)) {
            throw LiveGovernanceException.invalid(
                    "STAGE_NOT_MANAGED",
                    "Stage requests apply to broadcast-style events (PUBLIC_BROADCAST / HYBRID_EVENT / EMERGENCY_BRIEFING).");
        }
    }

    private LiveEventStageRequestEntity requireRequest(UUID eventId, UUID requestId) {
        LiveEventStageRequestEntity request = stageRequests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Stage request not found: " + requestId));
        if (!eventId.equals(request.getEventId())) {
            throw new IllegalArgumentException("Stage request does not belong to this event");
        }
        return request;
    }

    private void emitStageEvent(LiveEventEntity event, LiveEventStageRequestEntity request, String eventType) {
        emitStageEvent(event, request, request.getParticipantId(), eventType, request.getDecidedBy());
    }

    private void emitStageEvent(LiveEventEntity event, LiveEventStageRequestEntity request,
                                String participantId, String eventType, String decidedBy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.getId().toString());
        payload.put("title", event.getTitle());
        payload.put("participantId", participantId);
        if (request != null) {
            payload.put("requestId", request.getId() == null ? null : request.getId().toString());
            payload.put("status", request.getStatus());
        }
        if (decidedBy != null) {
            payload.put("decidedBy", decidedBy);
        }
        emitter.emit(event.getTenantId(), "LIVE_STAGE_REQUEST", event.getId().toString(),
                eventType, "LIVE_EVENT", event.getId().toString(), payload);
    }

    private Map<String, Object> requestView(LiveEventStageRequestEntity r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", r.getId());
        out.put("status", r.getStatus());
        out.put("requestedAt", r.getRequestedAt());
        out.put("decidedBy", r.getDecidedBy());
        out.put("decidedAt", r.getDecidedAt());
        return out;
    }
}
