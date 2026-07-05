package zw.gov.mohcc.impilo.khuluma.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.khuluma.client.LiveServiceClient;
import zw.gov.mohcc.impilo.khuluma.client.RtcGatewayClient;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationEntity;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationLinkEntity;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationParticipantEntity;
import zw.gov.mohcc.impilo.khuluma.domain.MeetingActionItemEntity;
import zw.gov.mohcc.impilo.khuluma.domain.MeetingAdmissionEntity;
import zw.gov.mohcc.impilo.khuluma.repository.ConversationLinkRepository;
import zw.gov.mohcc.impilo.khuluma.repository.MeetingActionItemRepository;
import zw.gov.mohcc.impilo.khuluma.repository.MeetingAdmissionRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates multi-party virtual meetings / live events over <b>Impilo Live</b> (the
 * {@code live-service} live-events/webinars SoR). A meeting is a {@code MEETING} Khuluma
 * conversation (its chat thread + membership) linked to an Impilo Live event (objectType
 * {@code LIVE_EVENT}); the media room is an rtc-gateway MEETING-template session (KNOCK
 * lobby, grid layout) provisioned through Impilo Live. Composition is bidirectional and
 * Khuluma owns no live-event or media truth:
 * <ul>
 *   <li>{@link #create} — a Khuluma meeting IS a (private) Impilo Live event;</li>
 *   <li>{@link #fromEvent} — any existing Impilo Live event gains a Khuluma conversation
 *       (discussion thread + quick-join), idempotently;</li>
 *   <li>{@link #join} — tokens are minted by rtc-gateway against the MEETING template
 *       (HOST/COHOST/PARTICIPANT/OBSERVER grants); the KNOCK lobby is rtc-gateway's
 *       waiting-room, Khuluma only mirrors the decisions it makes
 *       ({@code khuluma_meeting_admissions}) and drives admit/deny as the meeting-domain
 *       moderator surface.</li>
 * </ul>
 * These are not parallel surfaces: a Khuluma "Meet" and an Impilo Live event are the same object,
 * reachable from either experience.
 */
@Service
public class MeetingService {

    private static final Logger log = LoggerFactory.getLogger(MeetingService.class);
    private static final String AGGREGATE = "Meeting";
    private static final String LINK_TYPE = "LIVE_EVENT";

    // MEETING session-template roles (contracts/schemas/session-templates/meeting.json).
    static final String ROLE_HOST = "HOST";
    static final String ROLE_COHOST = "COHOST";
    static final String ROLE_PARTICIPANT = "PARTICIPANT";
    static final String ROLE_OBSERVER = "OBSERVER";

    private final ConversationService conversations;
    private final ConversationLinkRepository links;
    private final LiveServiceClient liveService;
    private final RtcGatewayClient rtcGateway;
    private final MeetingAdmissionRepository admissions;
    private final MeetingActionItemRepository actionItems;
    private final MeetingInviteService invites;
    private final OutboxAppender outbox;
    private final RealtimeDispatcher realtime;

    public MeetingService(ConversationService conversations, ConversationLinkRepository links,
                          LiveServiceClient liveService, RtcGatewayClient rtcGateway,
                          MeetingAdmissionRepository admissions, MeetingActionItemRepository actionItems,
                          MeetingInviteService invites, OutboxAppender outbox, RealtimeDispatcher realtime) {
        this.conversations = conversations;
        this.links = links;
        this.liveService = liveService;
        this.rtcGateway = rtcGateway;
        this.admissions = admissions;
        this.actionItems = actionItems;
        this.invites = invites;
        this.outbox = outbox;
        this.realtime = realtime;
    }

    @Transactional
    public MeetingResult create(ActorContext ctx, String title,
                                List<ConversationService.NewParticipant> participants) {
        return create(ctx, title, participants, null);
    }

    /** {@code scheduledAt} (ISO-8601) passes through to the live event's startTime (scheduling SoR). */
    @Transactional
    public MeetingResult create(ActorContext ctx, String title,
                                List<ConversationService.NewParticipant> participants, String scheduledAt) {
        ConversationEntity conv = conversations.create(ctx, "MEETING", title, null, participants, null);
        int max = (participants != null ? participants.size() : 0) + 1;

        LiveServiceClient.CreateEventResult event = liveService.createEvent(
                title != null ? title : "Meeting", "VIRTUAL", "MEETING",
                conv.getConversationId().toString(), ctx.actorType(), ctx.actorId(), max, scheduledAt);

        if (event.available() && event.eventId() != null) {
            conversations.linkObject(ctx, conv.getConversationId(), LINK_TYPE, event.eventId(), "MEETING", null);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conv.getConversationId().toString());
        payload.put("event_id", event.eventId());
        payload.put("created_by", ctx.actorId());
        payload.put("scheduled_at", scheduledAt);
        outbox.append("impilo.khuluma.meeting.created.v1", AGGREGATE, conv.getConversationId().toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-created-" + conv.getConversationId(), payload,
                conv.getConversationId().toString(), conv.getConversationId().toString(), AGGREGATE);

        log.info("Meeting created [conversation={}, event={}, mediaAvailable={}, scheduledAt={}]",
                conv.getConversationId(), event.eventId(), event.available(), scheduledAt);
        return new MeetingResult(conv.getConversationId().toString(), event.eventId(),
                event.available(), event.error());
    }

    /**
     * Attach a Khuluma conversation to an EXISTING Impilo Live event, so the event gains a discussion
     * thread + quick-join. Idempotent: if a conversation is already linked to the event, it is reused.
     */
    @Transactional
    public MeetingResult fromEvent(ActorContext ctx, String eventId, String title,
                                   List<ConversationService.NewParticipant> participants) {
        var existing = links.findByTenantIdAndObjectTypeAndObjectId(ctx.tenantId(), LINK_TYPE, eventId)
                .stream().findFirst();
        if (existing.isPresent()) {
            return new MeetingResult(existing.get().getConversationId().toString(), eventId, true, null);
        }
        ConversationEntity conv = conversations.create(ctx, "MEETING",
                title != null ? title : "Live event discussion", null, participants, null);
        conversations.linkObject(ctx, conv.getConversationId(), LINK_TYPE, eventId, "MEETING", null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conv.getConversationId().toString());
        payload.put("event_id", eventId);
        payload.put("created_by", ctx.actorId());
        outbox.append("impilo.khuluma.meeting.from-event.v1", AGGREGATE, conv.getConversationId().toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-from-event-" + conv.getConversationId(), payload,
                conv.getConversationId().toString(), conv.getConversationId().toString(), AGGREGATE);

        log.info("Khuluma conversation attached to Impilo Live event [conversation={}, event={}]",
                conv.getConversationId(), eventId);
        return new MeetingResult(conv.getConversationId().toString(), eventId, true, null);
    }

    /** The Khuluma conversation (if any) anchored to an Impilo Live event — for the Impilo Live experience. */
    @Transactional(readOnly = true)
    public Optional<String> conversationForEvent(ActorContext ctx, String eventId) {
        return links.findByTenantIdAndObjectTypeAndObjectId(ctx.tenantId(), LINK_TYPE, eventId)
                .stream().map(l -> l.getConversationId().toString()).findFirst();
    }

    // ── join + KNOCK lobby ─────────────────────────────────────────────────────

    /**
     * Request to join the meeting's media room. Tokens are minted by rtc-gateway against the
     * MEETING template: HOST/COHOST are roomAdmins (lobby bypass), everyone else KNOCKs and
     * receives {@code WAITING} until a host admits them. The outcome is mirrored into
     * {@code khuluma_meeting_admissions}; a first-time knock emits
     * {@code meeting.admission.requested} to the meeting conversation so open host clients
     * surface the admit queue.
     */
    @Transactional
    public MeetingJoin join(ActorContext ctx, UUID conversationId) {
        ConversationEntity conv = conversations.get(ctx, conversationId);
        if (!conversations.isActiveParticipant(conversationId, ctx.actorId())) {
            throw new SecurityException("Actor is not a participant of meeting " + conversationId);
        }
        String eventId = eventIdOf(conversationId);
        ConversationParticipantEntity membership = membershipOf(conversationId, ctx.actorId()).orElse(null);
        String role = templateRoleFor(conv, membership, ctx.actorId());
        String displayName = membership != null && membership.getDisplayName() != null
                ? membership.getDisplayName() : ctx.actorId();

        // Ensure the live room + rtc session exist (idempotent), learn the rtc session id.
        LiveServiceClient.JoinRoomResult room = liveService.joinRoom(eventId, ctx.actorId(), ctx.actorType(), role);
        String rtcSessionId = room.available() ? room.rtcSessionId() : null;

        if (rtcSessionId == null) {
            // Degraded: no rtc session id — fall back to the legacy live-service token path so a
            // partially-configured estate still yields media (no lobby state available this way).
            LiveServiceClient.RoomTokenResult token = liveService.issueToken(eventId, ctx.actorId(), role);
            boolean ready = token.available() && token.accessToken() != null;
            appendJoinAudit(ctx, conversationId, eventId, role, ready ? "READY" : "UNAVAILABLE");
            return new MeetingJoin(conversationId.toString(), eventId,
                    ready ? MeetingJoin.STATUS_READY : MeetingJoin.STATUS_UNAVAILABLE, role, null,
                    ready, token.roomUrl(), token.accessToken(), token.provider(),
                    ready ? null : (token.error() != null ? token.error() : room.error()));
        }

        RtcGatewayClient.RtcSessionResult token =
                rtcGateway.issueToken(rtcSessionId, ctx.actorId(), displayName, role);
        String status = lobbyStatusOf(token);
        recordAdmission(ctx, conv, rtcSessionId, role, displayName, status);
        appendJoinAudit(ctx, conversationId, eventId, role, status);

        boolean ready = MeetingJoin.STATUS_READY.equals(status);
        return new MeetingJoin(conversationId.toString(), eventId, status, role, rtcSessionId,
                ready, token.roomUrl(), ready ? token.accessToken() : null, token.provider(),
                ready || MeetingJoin.STATUS_WAITING.equals(status) || MeetingJoin.STATUS_DENIED.equals(status)
                        ? null : token.error());
    }

    /** Host/cohost view of the meeting lobby + roster (admission mirror overlaid with the live rtc roster). */
    @Transactional(readOnly = true)
    public List<MeetingAdmissionEntity> lobby(ActorContext ctx, UUID conversationId) {
        ConversationEntity conv = conversations.get(ctx, conversationId);
        requireHostOrCohost(ctx, conv);
        return admissions.findByConversationIdOrderByRequestedAtAsc(conversationId);
    }

    /** Admit a knocking participant: drives rtc-gateway's waiting-room, then mirrors the decision. */
    @Transactional
    public MeetingAdmissionEntity admit(ActorContext ctx, UUID conversationId, String actorId) {
        return decide(ctx, conversationId, actorId, true, null);
    }

    /** Deny a knocking participant. */
    @Transactional
    public MeetingAdmissionEntity deny(ActorContext ctx, UUID conversationId, String actorId, String reason) {
        return decide(ctx, conversationId, actorId, false, reason);
    }

    private MeetingAdmissionEntity decide(ActorContext ctx, UUID conversationId, String actorId,
                                          boolean admitted, String reason) {
        ConversationEntity conv = conversations.get(ctx, conversationId);
        requireHostOrCohost(ctx, conv);
        MeetingAdmissionEntity admission = admissions.findByConversationIdAndActorId(conversationId, actorId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No lobby request from " + actorId + " for meeting " + conversationId));
        if (admission.getRtcSessionId() == null) {
            throw new MeetingMediaUnavailableException("Meeting has no media session to admit into yet");
        }
        // Transport truth first: the rtc-gateway waiting-room is what actually gates the token.
        boolean applied = admitted
                ? rtcGateway.admit(admission.getRtcSessionId(), actorId, ctx.actorId())
                : rtcGateway.deny(admission.getRtcSessionId(), actorId, reason);
        if (!applied) {
            throw new MeetingMediaUnavailableException("rtc-gateway could not apply the lobby decision — retry");
        }
        admission.setStatus(admitted ? MeetingAdmissionEntity.STATUS_ADMITTED : MeetingAdmissionEntity.STATUS_DENIED);
        admission.setDecidedBy(ctx.actorId());
        admission.setDecidedAt(OffsetDateTime.now());
        admission.setDeniedReason(admitted ? null : reason);
        admission.setUpdatedAt(OffsetDateTime.now());
        admissions.save(admission);

        String verb = admitted ? "admitted" : "denied";
        Map<String, Object> payload = admissionPayload(admission);
        outbox.append("impilo.khuluma.meeting.admission-" + verb + ".v1", AGGREGATE, conversationId.toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-adm-" + verb + "-" + conversationId + "-" + actorId + "-" + admission.getDecidedAt(),
                payload, conversationId.toString(), conversationId.toString(), AGGREGATE);
        realtime.publish(ctx.tenantId(), "conversation:" + conversationId, "meeting.admission." + verb, payload);
        // Also poke the waiting participant's personal channel — they may not have the
        // conversation channel open while sitting in the lobby.
        realtime.publish(ctx.tenantId(), "actor:" + actorId, "meeting.admission." + verb, payload);
        return admission;
    }

    // ── co-host management ─────────────────────────────────────────────────────

    /**
     * Assign or revoke the COHOST role. Only the meeting host (conversation creator) may manage
     * cohosts; the role is persisted on the conversation participant row and is what
     * {@link #join} maps to the COHOST media grant (pub + roomAdmin) at token minting.
     */
    @Transactional
    public void setCohost(ActorContext ctx, UUID conversationId, String actorId, boolean cohost) {
        ConversationEntity conv = conversations.get(ctx, conversationId);
        if (!ctx.actorId().equals(conv.getCreatedBy())) {
            throw new SecurityException("Only the meeting host can manage cohosts");
        }
        ConversationParticipantEntity membership = membershipOf(conversationId, actorId)
                .filter(ConversationParticipantEntity::isActive)
                .orElseThrow(() -> new NoSuchElementException(
                        actorId + " is not an active participant of meeting " + conversationId));
        conversations.addParticipant(ctx, conversationId, actorId, membership.getActorType(),
                membership.getDisplayName(), cohost ? ROLE_COHOST : "MEMBER");

        String verb = cohost ? "assigned" : "revoked";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conversationId.toString());
        payload.put("actor_id", actorId);
        payload.put("by", ctx.actorId());
        outbox.append("impilo.khuluma.meeting.cohost-" + verb + ".v1", AGGREGATE, conversationId.toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-cohost-" + verb + "-" + conversationId + "-" + actorId + "-" + OffsetDateTime.now(),
                payload, conversationId.toString(), conversationId.toString(), AGGREGATE);
        realtime.publish(ctx.tenantId(), "conversation:" + conversationId, "meeting.cohost." + verb, payload);
    }

    // ── raise-hand + reactions (realtime, audit-only persistence) ──────────────

    @Transactional
    public void raiseHand(ActorContext ctx, UUID conversationId, boolean raised) {
        requireMember(ctx, conversationId);
        String verb = raised ? "raised" : "lowered";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conversationId.toString());
        payload.put("actor_id", ctx.actorId());
        payload.put("raised", raised);
        outbox.append("impilo.khuluma.meeting.hand-" + verb + ".v1", AGGREGATE, conversationId.toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-hand-" + conversationId + "-" + ctx.actorId() + "-" + UUID.randomUUID(),
                payload, conversationId.toString(), conversationId.toString(), AGGREGATE);
        realtime.publish(ctx.tenantId(), "conversation:" + conversationId, "meeting.hand." + verb, payload);
    }

    @Transactional
    public void react(ActorContext ctx, UUID conversationId, String reaction) {
        requireMember(ctx, conversationId);
        if (reaction == null || reaction.isBlank() || reaction.length() > 16) {
            throw new IllegalArgumentException("Reaction must be a short emoji/keyword (1-16 chars)");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conversationId.toString());
        payload.put("actor_id", ctx.actorId());
        payload.put("reaction", reaction.trim());
        outbox.append("impilo.khuluma.meeting.reaction.v1", AGGREGATE, conversationId.toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-react-" + conversationId + "-" + ctx.actorId() + "-" + UUID.randomUUID(),
                payload, conversationId.toString(), conversationId.toString(), AGGREGATE);
        realtime.publish(ctx.tenantId(), "conversation:" + conversationId, "meeting.reaction", payload);
    }

    // ── action items ───────────────────────────────────────────────────────────

    @Transactional
    public MeetingActionItemEntity createActionItem(ActorContext ctx, UUID conversationId, String description,
                                                    String assigneeId, String assigneeDisplayName, LocalDate dueDate) {
        requireMember(ctx, conversationId);
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Action item description is required");
        }
        MeetingActionItemEntity item = new MeetingActionItemEntity();
        item.setTenantId(ctx.tenantId());
        item.setConversationId(conversationId);
        item.setDescription(description.trim());
        item.setAssigneeId(assigneeId);
        item.setAssigneeDisplayName(assigneeDisplayName);
        item.setDueDate(dueDate);
        item.setCreatedBy(ctx.actorId());
        actionItems.save(item);

        Map<String, Object> payload = actionItemPayload(item);
        outbox.append("impilo.khuluma.meeting.action-item-created.v1", AGGREGATE, item.getActionItemId().toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-ai-created-" + item.getActionItemId(), payload,
                conversationId.toString(), conversationId.toString(), AGGREGATE);
        realtime.publish(ctx.tenantId(), "conversation:" + conversationId, "meeting.action-item.created", payload);
        return item;
    }

    @Transactional(readOnly = true)
    public List<MeetingActionItemEntity> listActionItems(ActorContext ctx, UUID conversationId) {
        requireMember(ctx, conversationId);
        return actionItems.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    @Transactional
    public MeetingActionItemEntity updateActionItem(ActorContext ctx, UUID conversationId, UUID actionItemId,
                                                    String status, String description,
                                                    String assigneeId, String assigneeDisplayName, LocalDate dueDate) {
        requireMember(ctx, conversationId);
        MeetingActionItemEntity item = actionItems.findByActionItemIdAndTenantId(actionItemId, ctx.tenantId())
                .filter(i -> i.getConversationId().equals(conversationId))
                .orElseThrow(() -> new NoSuchElementException("Action item not found: " + actionItemId));
        if (status != null && !status.isBlank()) {
            item.setStatus(normalizeActionStatus(status));
        }
        if (description != null && !description.isBlank()) {
            item.setDescription(description.trim());
        }
        if (assigneeId != null) {
            item.setAssigneeId(assigneeId.isBlank() ? null : assigneeId);
            item.setAssigneeDisplayName(assigneeDisplayName);
        }
        if (dueDate != null) {
            item.setDueDate(dueDate);
        }
        item.setUpdatedAt(OffsetDateTime.now());
        actionItems.save(item);

        Map<String, Object> payload = actionItemPayload(item);
        outbox.append("impilo.khuluma.meeting.action-item-updated.v1", AGGREGATE, item.getActionItemId().toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-ai-updated-" + item.getActionItemId() + "-" + item.getUpdatedAt(), payload,
                conversationId.toString(), conversationId.toString(), AGGREGATE);
        realtime.publish(ctx.tenantId(), "conversation:" + conversationId, "meeting.action-item.updated", payload);
        return item;
    }

    // ── invite links ───────────────────────────────────────────────────────────

    /** Mint an HMAC-signed invite link token (host/cohost only). */
    @Transactional
    public MeetingInviteService.MintedInvite createInvite(ActorContext ctx, UUID conversationId,
                                                          String role, Long ttlSeconds) {
        ConversationEntity conv = conversations.get(ctx, conversationId);
        requireHostOrCohost(ctx, conv);
        MeetingInviteService.MintedInvite invite =
                invites.mint(ctx.tenantId(), conversationId, role, ttlSeconds, ctx.actorId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conversationId.toString());
        payload.put("minted_by", ctx.actorId());
        payload.put("role", invite.role());
        payload.put("expires_at", invite.expiresAt().toString());
        outbox.append("impilo.khuluma.meeting.invite-minted.v1", AGGREGATE, conversationId.toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-invite-" + conversationId + "-" + ctx.actorId() + "-" + invite.expiresAt(),
                payload, conversationId.toString(), conversationId.toString(), AGGREGATE);
        return invite;
    }

    /**
     * Resolve an invite token for the ACTING (authenticated) actor: verifies the HMAC + expiry +
     * tenant, joins them to the meeting conversation with the embedded role, and returns the
     * meeting handle so the client can route to the join flow.
     */
    @Transactional
    public MeetingResult resolveInvite(ActorContext ctx, String token) {
        MeetingInviteService.InviteClaims claims = invites.verify(ctx.tenantId(), token);
        conversations.addSelfByInvite(ctx, claims.conversationId(), claims.role(), claims.mintedBy());
        String eventId = links.findByConversationId(claims.conversationId()).stream()
                .filter(l -> LINK_TYPE.equals(l.getObjectType()))
                .map(ConversationLinkEntity::getObjectId)
                .findFirst().orElse(null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", claims.conversationId().toString());
        payload.put("actor_id", ctx.actorId());
        payload.put("role", claims.role());
        payload.put("minted_by", claims.mintedBy());
        outbox.append("impilo.khuluma.meeting.invite-resolved.v1", AGGREGATE, claims.conversationId().toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-invite-resolved-" + claims.conversationId() + "-" + ctx.actorId(),
                payload, claims.conversationId().toString(), claims.conversationId().toString(), AGGREGATE);
        return new MeetingResult(claims.conversationId().toString(), eventId, true, null);
    }

    // ── meeting detail + attendance ────────────────────────────────────────────

    /** Aggregated meeting detail for members: schedule (live-service SoR read-through) + attendance. */
    public record MeetingDetail(String conversationId, String eventId, String title, String eventStatus,
                                String scheduledAt, String endTime, String hostId, List<String> cohostIds,
                                List<MeetingAdmissionEntity> attendance) {}

    @Transactional(readOnly = true)
    public MeetingDetail detail(ActorContext ctx, UUID conversationId) {
        ConversationEntity conv = conversations.get(ctx, conversationId);
        requireMember(ctx, conversationId);
        String eventId = links.findByConversationId(conversationId).stream()
                .filter(l -> LINK_TYPE.equals(l.getObjectType()))
                .map(ConversationLinkEntity::getObjectId)
                .findFirst().orElse(null);
        LiveServiceClient.EventResult event = eventId != null
                ? liveService.getEvent(eventId)
                : LiveServiceClient.EventResult.unavailable("no live event linked");
        List<String> cohosts = conversations.participantsOf(conversationId).stream()
                .filter(p -> p.isActive() && ROLE_COHOST.equalsIgnoreCase(p.getRole()))
                .map(ConversationParticipantEntity::getActorId)
                .toList();
        return new MeetingDetail(conversationId.toString(), eventId, conv.getTitle(),
                event.available() ? event.status() : null,
                event.available() ? event.startTime() : null,
                event.available() ? event.endTime() : null,
                conv.getCreatedBy(), cohosts,
                admissions.findByConversationIdOrderByRequestedAtAsc(conversationId));
    }

    @Transactional
    public void end(ActorContext ctx, UUID conversationId) {
        ConversationEntity conv = conversations.get(ctx, conversationId);
        requireHostOrCohost(ctx, conv);
        String eventId = eventIdOf(conversationId);
        liveService.endRoom(eventId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conversationId.toString());
        payload.put("event_id", eventId);
        outbox.append("impilo.khuluma.meeting.ended.v1", AGGREGATE, conversationId.toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-end-" + conversationId, payload,
                conversationId.toString(), conversationId.toString(), AGGREGATE);
        realtime.publish(ctx.tenantId(), "conversation:" + conversationId, "meeting.ended", payload);
    }

    // ── internals ──────────────────────────────────────────────────────────────

    private String eventIdOf(UUID conversationId) {
        return links.findByConversationId(conversationId).stream()
                .filter(l -> LINK_TYPE.equals(l.getObjectType()))
                .map(ConversationLinkEntity::getObjectId)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No live event linked to meeting " + conversationId));
    }

    private Optional<ConversationParticipantEntity> membershipOf(UUID conversationId, String actorId) {
        return conversations.participantsOf(conversationId).stream()
                .filter(p -> actorId.equals(p.getActorId()))
                .findFirst();
    }

    /** Map the conversation membership to the MEETING template role used for token grants. */
    private String templateRoleFor(ConversationEntity conv, ConversationParticipantEntity membership,
                                   String actorId) {
        if (actorId.equals(conv.getCreatedBy())) {
            return ROLE_HOST;
        }
        String role = membership != null && membership.getRole() != null
                ? membership.getRole().trim().toUpperCase(Locale.ROOT) : "";
        return switch (role) {
            case ROLE_COHOST -> ROLE_COHOST;
            case ROLE_OBSERVER -> ROLE_OBSERVER;
            default -> ROLE_PARTICIPANT;
        };
    }

    private void requireMember(ActorContext ctx, UUID conversationId) {
        conversations.get(ctx, conversationId);
        if (!conversations.isActiveParticipant(conversationId, ctx.actorId())) {
            throw new SecurityException("Actor is not a participant of meeting " + conversationId);
        }
    }

    private void requireHostOrCohost(ActorContext ctx, ConversationEntity conv) {
        if (ctx.actorId().equals(conv.getCreatedBy())) {
            return;
        }
        boolean cohost = membershipOf(conv.getConversationId(), ctx.actorId())
                .filter(ConversationParticipantEntity::isActive)
                .map(p -> ROLE_COHOST.equalsIgnoreCase(p.getRole()))
                .orElse(false);
        if (!cohost) {
            throw new SecurityException("Only the meeting host or a cohost may perform this action");
        }
    }

    private static String lobbyStatusOf(RtcGatewayClient.RtcSessionResult token) {
        if (!token.available()) {
            return MeetingJoin.STATUS_UNAVAILABLE;
        }
        String status = token.status() == null ? "" : token.status().trim().toUpperCase(Locale.ROOT);
        if ("WAITING".equals(status)) {
            return MeetingJoin.STATUS_WAITING;
        }
        if ("DENIED".equals(status)) {
            return MeetingJoin.STATUS_DENIED;
        }
        return token.accessToken() != null ? MeetingJoin.STATUS_READY : MeetingJoin.STATUS_UNAVAILABLE;
    }

    /** Mirror the lobby outcome into khuluma_meeting_admissions; first knock emits admission.requested. */
    private void recordAdmission(ActorContext ctx, ConversationEntity conv, String rtcSessionId,
                                 String role, String displayName, String status) {
        UUID conversationId = conv.getConversationId();
        MeetingAdmissionEntity admission = admissions
                .findByConversationIdAndActorId(conversationId, ctx.actorId())
                .orElseGet(MeetingAdmissionEntity::new);
        boolean firstKnock = admission.getConversationId() == null
                && MeetingJoin.STATUS_WAITING.equals(status);
        admission.setConversationId(conversationId);
        admission.setTenantId(ctx.tenantId());
        admission.setActorId(ctx.actorId());
        admission.setActorType(ctx.actorType());
        admission.setDisplayName(displayName);
        admission.setMeetingRole(role);
        admission.setRtcSessionId(rtcSessionId);
        switch (status) {
            case MeetingJoin.STATUS_READY -> {
                if (!MeetingAdmissionEntity.STATUS_ADMITTED.equals(admission.getStatus())
                        || admission.getDecidedAt() == null) {
                    admission.setStatus(MeetingAdmissionEntity.STATUS_ADMITTED);
                    if (admission.getDecidedBy() == null) {
                        admission.setDecidedBy((ROLE_HOST.equals(role) || ROLE_COHOST.equals(role))
                                ? "ROOM_ADMIN_AUTO" : admission.getDecidedBy());
                        admission.setDecidedAt(OffsetDateTime.now());
                    }
                }
            }
            case MeetingJoin.STATUS_WAITING -> admission.setStatus(MeetingAdmissionEntity.STATUS_WAITING);
            case MeetingJoin.STATUS_DENIED -> admission.setStatus(MeetingAdmissionEntity.STATUS_DENIED);
            default -> { /* UNAVAILABLE — keep prior lobby state */ }
        }
        admission.setUpdatedAt(OffsetDateTime.now());
        admissions.save(admission);

        if (firstKnock) {
            Map<String, Object> payload = admissionPayload(admission);
            outbox.append("impilo.khuluma.meeting.admission-requested.v1", AGGREGATE,
                    conversationId.toString(), ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                    "meeting-adm-requested-" + conversationId + "-" + ctx.actorId(), payload,
                    conversationId.toString(), conversationId.toString(), AGGREGATE);
            realtime.publish(ctx.tenantId(), "conversation:" + conversationId,
                    "meeting.admission.requested", payload);
        }
    }

    private void appendJoinAudit(ActorContext ctx, UUID conversationId, String eventId,
                                 String role, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conversationId.toString());
        payload.put("event_id", eventId);
        payload.put("actor_id", ctx.actorId());
        payload.put("role", role);
        payload.put("status", status);
        outbox.append("impilo.khuluma.meeting.joined.v1", AGGREGATE, conversationId.toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-join-" + conversationId + "-" + ctx.actorId() + "-" + UUID.randomUUID(),
                payload, conversationId.toString(), conversationId.toString(), AGGREGATE);
    }

    private static Map<String, Object> admissionPayload(MeetingAdmissionEntity a) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", a.getConversationId().toString());
        payload.put("actor_id", a.getActorId());
        payload.put("display_name", a.getDisplayName());
        payload.put("role", a.getMeetingRole());
        payload.put("status", a.getStatus());
        payload.put("decided_by", a.getDecidedBy());
        payload.put("denied_reason", a.getDeniedReason());
        return payload;
    }

    private static Map<String, Object> actionItemPayload(MeetingActionItemEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action_item_id", item.getActionItemId().toString());
        payload.put("conversation_id", item.getConversationId().toString());
        payload.put("description", item.getDescription());
        payload.put("assignee_id", item.getAssigneeId());
        payload.put("assignee_display_name", item.getAssigneeDisplayName());
        payload.put("due_date", item.getDueDate() != null ? item.getDueDate().toString() : null);
        payload.put("status", item.getStatus());
        payload.put("created_by", item.getCreatedBy());
        return payload;
    }

    private static String normalizeActionStatus(String status) {
        String upper = status.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "OPEN", "IN_PROGRESS", "DONE", "CANCELLED" -> upper;
            default -> throw new IllegalArgumentException("Unknown action item status: " + status);
        };
    }
}
