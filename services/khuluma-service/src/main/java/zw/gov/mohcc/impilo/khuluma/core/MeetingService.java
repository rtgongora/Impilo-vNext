package zw.gov.mohcc.impilo.khuluma.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.khuluma.client.LiveServiceClient;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationEntity;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationLinkEntity;
import zw.gov.mohcc.impilo.khuluma.repository.ConversationLinkRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Orchestrates multi-party virtual meetings / live events over <b>Impilo Live</b> (the
 * {@code live-service} live-events/webinars SoR). A meeting is a {@code MEETING} Khuluma
 * conversation (its chat thread + membership) linked to an Impilo Live event (objectType
 * {@code LIVE_EVENT}); the media room + per-participant LiveKit token come from Impilo Live's
 * rtc-gateway provider. Composition is bidirectional and Khuluma owns no live-event truth:
 * <ul>
 *   <li>{@link #create} — a Khuluma meeting IS a (private) Impilo Live event;</li>
 *   <li>{@link #fromEvent} — any existing Impilo Live event gains a Khuluma conversation
 *       (discussion thread + quick-join), idempotently.</li>
 * </ul>
 * These are not parallel surfaces: a Khuluma "Meet" and an Impilo Live event are the same object,
 * reachable from either experience.
 */
@Service
public class MeetingService {

    private static final Logger log = LoggerFactory.getLogger(MeetingService.class);
    private static final String AGGREGATE = "Meeting";
    private static final String LINK_TYPE = "LIVE_EVENT";

    private final ConversationService conversations;
    private final ConversationLinkRepository links;
    private final LiveServiceClient liveService;
    private final OutboxAppender outbox;

    public MeetingService(ConversationService conversations, ConversationLinkRepository links,
                          LiveServiceClient liveService, OutboxAppender outbox) {
        this.conversations = conversations;
        this.links = links;
        this.liveService = liveService;
        this.outbox = outbox;
    }

    @Transactional
    public MeetingResult create(ActorContext ctx, String title,
                                List<ConversationService.NewParticipant> participants) {
        ConversationEntity conv = conversations.create(ctx, "MEETING", title, null, participants, null);
        int max = (participants != null ? participants.size() : 0) + 1;

        LiveServiceClient.CreateEventResult event = liveService.createEvent(
                title != null ? title : "Meeting", "VIRTUAL", "MEETING",
                conv.getConversationId().toString(), ctx.actorType(), ctx.actorId(), max);

        if (event.available() && event.eventId() != null) {
            conversations.linkObject(ctx, conv.getConversationId(), LINK_TYPE, event.eventId(), "MEETING", null);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conv.getConversationId().toString());
        payload.put("event_id", event.eventId());
        payload.put("created_by", ctx.actorId());
        outbox.append("impilo.khuluma.meeting.created.v1", AGGREGATE, conv.getConversationId().toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-created-" + conv.getConversationId(), payload,
                conv.getConversationId().toString(), conv.getConversationId().toString(), AGGREGATE);

        log.info("Meeting created [conversation={}, event={}, mediaAvailable={}]",
                conv.getConversationId(), event.eventId(), event.available());
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
    public java.util.Optional<String> conversationForEvent(ActorContext ctx, String eventId) {
        return links.findByTenantIdAndObjectTypeAndObjectId(ctx.tenantId(), LINK_TYPE, eventId)
                .stream().map(l -> l.getConversationId().toString()).findFirst();
    }

    @Transactional
    public MeetingJoin join(ActorContext ctx, UUID conversationId) {
        ConversationEntity conv = conversations.get(ctx, conversationId);
        if (!conversations.isActiveParticipant(conversationId, ctx.actorId())) {
            throw new SecurityException("Actor is not a participant of meeting " + conversationId);
        }
        String eventId = eventIdOf(conversationId);
        String role = ctx.actorId().equals(conv.getCreatedBy()) ? "HOST" : "ATTENDEE";

        // Ensure the room exists, then mint this participant's media token.
        liveService.joinRoom(eventId, ctx.actorId(), ctx.actorType(), role);
        LiveServiceClient.RoomTokenResult token = liveService.issueToken(eventId, ctx.actorId(), role);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conversationId.toString());
        payload.put("event_id", eventId);
        payload.put("actor_id", ctx.actorId());
        payload.put("role", role);
        outbox.append("impilo.khuluma.meeting.joined.v1", AGGREGATE, conversationId.toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-join-" + conversationId + "-" + ctx.actorId() + "-" + System.identityHashCode(token),
                payload, conversationId.toString(), conversationId.toString(), AGGREGATE);

        return new MeetingJoin(conversationId.toString(), eventId, token.available(),
                token.roomUrl(), token.accessToken(), token.provider(), token.error());
    }

    @Transactional
    public void end(ActorContext ctx, UUID conversationId) {
        conversations.get(ctx, conversationId);
        String eventId = eventIdOf(conversationId);
        liveService.endRoom(eventId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conversationId.toString());
        payload.put("event_id", eventId);
        outbox.append("impilo.khuluma.meeting.ended.v1", AGGREGATE, conversationId.toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "meeting-end-" + conversationId, payload,
                conversationId.toString(), conversationId.toString(), AGGREGATE);
    }

    private String eventIdOf(UUID conversationId) {
        return links.findByConversationId(conversationId).stream()
                .filter(l -> LINK_TYPE.equals(l.getObjectType()))
                .map(ConversationLinkEntity::getObjectId)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No live event linked to meeting " + conversationId));
    }
}
