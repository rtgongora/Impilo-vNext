package zw.gov.mohcc.impilo.khuluma.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationEntity;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationLinkEntity;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationParticipantEntity;
import zw.gov.mohcc.impilo.khuluma.repository.ConversationLinkRepository;
import zw.gov.mohcc.impilo.khuluma.repository.ConversationParticipantRepository;
import zw.gov.mohcc.impilo.khuluma.repository.ConversationRepository;
import zw.gov.mohcc.impilo.khuluma.repository.MessageRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Owns the unified conversation index: create, inbox listing, detail read, and
 * participant/object-link management. Every mutation appends a domain/audit event.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final String AGGREGATE = "Conversation";

    private final ConversationRepository conversations;
    private final ConversationParticipantRepository participants;
    private final ConversationLinkRepository links;
    private final MessageRepository messages;
    private final OutboxAppender outbox;

    public ConversationService(ConversationRepository conversations,
                               ConversationParticipantRepository participants,
                               ConversationLinkRepository links,
                               MessageRepository messages,
                               OutboxAppender outbox) {
        this.conversations = conversations;
        this.participants = participants;
        this.links = links;
        this.messages = messages;
        this.outbox = outbox;
    }

    /** A participant to seed onto a new conversation. */
    public record NewParticipant(String actorId, String actorType, String displayName, String role) {}

    /** An object link to seed onto a new conversation. */
    public record NewLink(String objectType, String objectId, String linkRole, String note) {}

    @Transactional
    public ConversationEntity create(ActorContext ctx, String type, String title, String topic,
                                     List<NewParticipant> seedParticipants, List<NewLink> seedLinks) {
        ConversationEntity conv = new ConversationEntity();
        conv.setTenantId(ctx.tenantId());
        conv.setConversationType(normalizeType(type));
        conv.setTitle(title);
        conv.setTopic(topic);
        conv.setCreatedBy(ctx.actorId());
        conv.setCreatedByType(ctx.actorType());
        conversations.save(conv);

        // Creator is always an OWNER participant.
        upsertParticipant(conv, ctx.actorId(), ctx.actorType(), null, "OWNER");
        if (seedParticipants != null) {
            for (NewParticipant p : seedParticipants) {
                if (p.actorId() != null && !p.actorId().isBlank() && !p.actorId().equals(ctx.actorId())) {
                    upsertParticipant(conv, p.actorId(), p.actorType(), p.displayName(),
                            p.role() != null ? p.role() : "MEMBER");
                }
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conv.getConversationId().toString());
        payload.put("conversation_type", conv.getConversationType());
        payload.put("created_by", ctx.actorId());
        outbox.append("impilo.khuluma.conversation.created.v1", AGGREGATE,
                conv.getConversationId().toString(), ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "conv-created-" + conv.getConversationId(), payload,
                conv.getConversationId().toString(), conv.getConversationId().toString(), AGGREGATE);

        // Object links seeded at creation are governed actions too — audit each.
        if (seedLinks != null) {
            for (NewLink l : seedLinks) {
                if (l.objectType() != null && l.objectId() != null) {
                    persistLink(conv, ctx, l.objectType(), l.objectId(), l.linkRole(), l.note());
                    emitLinkedEvent(ctx, conv.getConversationId(), l.objectType(), l.objectId());
                }
            }
        }

        log.info("Created conversation [id={}, type={}, by={}]",
                conv.getConversationId(), conv.getConversationType(), ctx.actorId());
        return conv;
    }

    /** The requesting actor's inbox: conversations they are an active participant of, newest first. */
    @Transactional(readOnly = true)
    public List<ConversationSummary> inbox(ActorContext ctx) {
        List<ConversationParticipantEntity> memberships =
                participants.findByTenantIdAndActorIdAndActiveTrue(ctx.tenantId(), ctx.actorId());
        List<ConversationSummary> result = new ArrayList<>();
        for (ConversationParticipantEntity m : memberships) {
            conversations.findByConversationIdAndTenantId(m.getConversationId(), ctx.tenantId())
                    .ifPresent(conv -> {
                        long unread = messages.countUnread(conv.getConversationId(), ctx.actorId(), m.getLastReadAt());
                        result.add(new ConversationSummary(conv, m.getRole(), m.isMuted(), unread));
                    });
        }
        result.sort(Comparator.comparing(
                (ConversationSummary s) -> s.conversation().getLastMessageAt(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Transactional(readOnly = true)
    public ConversationEntity get(ActorContext ctx, UUID conversationId) {
        return conversations.findByConversationIdAndTenantId(conversationId, ctx.tenantId())
                .orElseThrow(() -> new NoSuchElementException("Conversation not found: " + conversationId));
    }

    @Transactional(readOnly = true)
    public List<ConversationParticipantEntity> participantsOf(UUID conversationId) {
        return participants.findByConversationId(conversationId);
    }

    @Transactional(readOnly = true)
    public List<ConversationLinkEntity> linksOf(UUID conversationId) {
        return links.findByConversationId(conversationId);
    }

    @Transactional(readOnly = true)
    public boolean isActiveParticipant(UUID conversationId, String actorId) {
        return participants.existsByConversationIdAndActorIdAndActiveTrue(conversationId, actorId);
    }

    /**
     * Mutating a conversation's membership or its clinical-object links is an insider operation:
     * the acting actor MUST already be an active participant. Without this, any authenticated actor
     * in the tenant could add themselves to an arbitrary conversation and then read its (PHI) messages
     * — the read/send paths are membership-gated, so the membership mutation must be gated too.
     */
    private void requireActiveParticipant(ActorContext ctx, UUID conversationId) {
        if (!participants.existsByConversationIdAndActorIdAndActiveTrue(conversationId, ctx.actorId())) {
            throw new SecurityException("Actor is not an active participant of conversation " + conversationId);
        }
    }

    @Transactional
    public ConversationParticipantEntity addParticipant(ActorContext ctx, UUID conversationId,
                                                        String actorId, String actorType,
                                                        String displayName, String role) {
        ConversationEntity conv = get(ctx, conversationId);
        requireActiveParticipant(ctx, conversationId);
        ConversationParticipantEntity p = upsertParticipant(conv, actorId, actorType, displayName,
                role != null ? role : "MEMBER");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conversationId.toString());
        payload.put("actor_id", actorId);
        payload.put("added_by", ctx.actorId());
        outbox.append("impilo.khuluma.conversation.participant-added.v1", AGGREGATE,
                conversationId.toString(), ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "conv-partadd-" + conversationId + "-" + actorId, payload,
                conversationId.toString(), conversationId.toString(), AGGREGATE);
        return p;
    }

    @Transactional
    public void removeParticipant(ActorContext ctx, UUID conversationId, String actorId) {
        get(ctx, conversationId);
        requireActiveParticipant(ctx, conversationId);
        ConversationParticipantEntity p = participants.findByConversationIdAndActorId(conversationId, actorId)
                .orElseThrow(() -> new NoSuchElementException("Participant not found: " + actorId));
        p.setActive(false);
        p.setLeftAt(OffsetDateTime.now());
        participants.save(p);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conversationId.toString());
        payload.put("actor_id", actorId);
        payload.put("removed_by", ctx.actorId());
        outbox.append("impilo.khuluma.conversation.participant-removed.v1", AGGREGATE,
                conversationId.toString(), ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "conv-partrem-" + conversationId + "-" + actorId + "-" + System.identityHashCode(p),
                payload, conversationId.toString(), conversationId.toString(), AGGREGATE);
    }

    @Transactional
    public ConversationLinkEntity linkObject(ActorContext ctx, UUID conversationId,
                                             String objectType, String objectId, String linkRole, String note) {
        ConversationEntity conv = get(ctx, conversationId);
        requireActiveParticipant(ctx, conversationId);
        ConversationLinkEntity link = persistLink(conv, ctx, objectType, objectId, linkRole, note);
        emitLinkedEvent(ctx, conversationId, objectType, objectId);
        return link;
    }

    private void emitLinkedEvent(ActorContext ctx, UUID conversationId, String objectType, String objectId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conversationId.toString());
        payload.put("object_type", objectType);
        payload.put("object_id", objectId);
        outbox.append("impilo.khuluma.conversation.linked.v1", AGGREGATE,
                conversationId.toString(), ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "conv-link-" + conversationId + "-" + objectType + "-" + objectId, payload,
                conversationId.toString(), conversationId.toString(), AGGREGATE);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private ConversationParticipantEntity upsertParticipant(ConversationEntity conv, String actorId,
                                                            String actorType, String displayName, String role) {
        ConversationParticipantEntity p = participants
                .findByConversationIdAndActorId(conv.getConversationId(), actorId)
                .orElseGet(ConversationParticipantEntity::new);
        p.setConversationId(conv.getConversationId());
        p.setTenantId(conv.getTenantId());
        p.setActorId(actorId);
        p.setActorType(actorType != null ? actorType : "PROVIDER");
        if (displayName != null) {
            p.setDisplayName(displayName);
        }
        p.setRole(role);
        if (!p.isActive()) {
            p.setActive(true);
            p.setLeftAt(null);
            p.setJoinedAt(OffsetDateTime.now());
        }
        return participants.save(p);
    }

    private ConversationLinkEntity persistLink(ConversationEntity conv, ActorContext ctx,
                                               String objectType, String objectId, String linkRole, String note) {
        ConversationLinkEntity link = links
                .findByConversationIdAndObjectTypeAndObjectId(conv.getConversationId(), objectType, objectId)
                .orElseGet(ConversationLinkEntity::new);
        link.setConversationId(conv.getConversationId());
        link.setTenantId(conv.getTenantId());
        link.setObjectType(objectType);
        link.setObjectId(objectId);
        link.setLinkRole(linkRole);
        link.setLinkedBy(ctx.actorId());
        link.setNote(note);
        return links.save(link);
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) return "DIRECT";
        String upper = type.trim().toUpperCase();
        return switch (upper) {
            case "DIRECT", "GROUP", "CHANNEL", "BROADCAST", "MEETING" -> upper;
            default -> "DIRECT";
        };
    }
}
