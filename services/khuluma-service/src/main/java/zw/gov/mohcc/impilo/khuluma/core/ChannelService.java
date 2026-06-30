package zw.gov.mohcc.impilo.khuluma.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationEntity;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationParticipantEntity;
import zw.gov.mohcc.impilo.khuluma.domain.MessageEntity;
import zw.gov.mohcc.impilo.khuluma.repository.ConversationParticipantRepository;
import zw.gov.mohcc.impilo.khuluma.repository.ConversationRepository;
import zw.gov.mohcc.impilo.khuluma.repository.MessageRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Channels, communities & broadcast (Phase 5 / W5, G-KH-02). Facility/programme channels and client
 * communities are scoped conversations (reusing the existing conversation/participant model); a broadcast
 * is a one-to-many message into a channel that only its owners/moderators may post. Members discover the
 * channels for their facility/programme/community via the scope index.
 */
@Service
public class ChannelService {

    private static final String AGGREGATE = "KHULUMA_CHANNEL";
    private static final Set<String> CONV_TYPES = Set.of("CHANNEL", "COMMUNITY", "BROADCAST");
    private static final Set<String> SCOPE_TYPES = Set.of("FACILITY", "PROGRAMME", "COMMUNITY");
    private static final Set<String> CAN_BROADCAST = Set.of("OWNER", "MODERATOR");

    private final ConversationRepository conversations;
    private final ConversationParticipantRepository participants;
    private final MessageRepository messages;
    private final OutboxAppender outbox;

    public ChannelService(ConversationRepository conversations, ConversationParticipantRepository participants,
                          MessageRepository messages, OutboxAppender outbox) {
        this.conversations = conversations;
        this.participants = participants;
        this.messages = messages;
        this.outbox = outbox;
    }

    public record NewChannel(String conversationType, String scopeType, UUID scopeRef,
                             String title, String topic) {}

    @Transactional
    public ConversationEntity create(UUID tenantId, String creatorId, String creatorType, NewChannel cmd) {
        String type = upper(cmd.conversationType(), CONV_TYPES, "CHANNEL");
        String scope = upper(cmd.scopeType(), SCOPE_TYPES, null);
        if (scope == null) {
            throw new IllegalArgumentException("A channel/community needs a scopeType (FACILITY|PROGRAMME|COMMUNITY)");
        }
        ConversationEntity conv = new ConversationEntity();
        conv.setTenantId(tenantId);
        conv.setConversationType(type);
        conv.setScopeType(scope);
        conv.setScopeRef(cmd.scopeRef());
        conv.setTitle(cmd.title());
        conv.setTopic(cmd.topic());
        conv.setCreatedBy(creatorId);
        conv.setCreatedByType(creatorType != null ? creatorType : "PROVIDER");
        conv.setStatus("ACTIVE");
        conversations.save(conv);

        addOrReactivate(conv, creatorId, creatorType, null, "OWNER");
        emit("impilo.khuluma.channel.created.v1", conv, creatorId);
        return conv;
    }

    /** Discover the active channels/communities for a scope (e.g. a facility's channels). */
    @Transactional(readOnly = true)
    public List<ConversationEntity> discover(UUID tenantId, String scopeType, UUID scopeRef) {
        return conversations.findByTenantIdAndScopeTypeAndScopeRefAndStatusOrderByCreatedAtDesc(
                tenantId, upper(scopeType, SCOPE_TYPES, "FACILITY"), scopeRef, "ACTIVE");
    }

    /** Join a channel/community (idempotent — re-joining reactivates a prior membership). */
    @Transactional
    public ConversationParticipantEntity join(UUID tenantId, UUID conversationId, String actorId,
                                              String actorType, String displayName) {
        ConversationEntity conv = requireConversation(tenantId, conversationId);
        ConversationParticipantEntity p = addOrReactivate(conv, actorId, actorType, displayName, "MEMBER");
        emit("impilo.khuluma.channel.joined.v1", conv, actorId);
        return p;
    }

    /** Leave a channel/community (sets the membership inactive). */
    @Transactional
    public void leave(UUID tenantId, UUID conversationId, String actorId) {
        ConversationEntity conv = requireConversation(tenantId, conversationId);
        participants.findByConversationIdAndActorId(conversationId, actorId).ifPresent(p -> {
            p.setActive(false);
            p.setLeftAt(OffsetDateTime.now());
            participants.save(p);
        });
        emit("impilo.khuluma.channel.left.v1", conv, actorId);
    }

    /**
     * Broadcast a message into a channel. Only an active OWNER/MODERATOR may broadcast — members are
     * recipients, not posters. Updates the conversation's last-message denormalisation + emits an event.
     */
    @Transactional
    public MessageEntity broadcast(UUID tenantId, UUID conversationId, String senderId,
                                   String senderType, String body) {
        ConversationEntity conv = requireConversation(tenantId, conversationId);
        ConversationParticipantEntity sender = participants.findByConversationIdAndActorId(conversationId, senderId)
                .filter(ConversationParticipantEntity::isActive)
                .orElseThrow(() -> new SecurityException("Only an active member may broadcast"));
        if (!CAN_BROADCAST.contains(sender.getRole())) {
            throw new SecurityException("Only an OWNER or MODERATOR may broadcast to a channel");
        }
        MessageEntity msg = new MessageEntity();
        msg.setConversationId(conversationId);
        msg.setTenantId(tenantId);
        msg.setSenderId(senderId);
        msg.setSenderType(senderType != null ? senderType : "PROVIDER");
        msg.setContentType("BROADCAST");
        msg.setBody(body);
        msg.setStatus("SENT");
        messages.save(msg);

        conv.setLastMessageId(msg.getMessageId());
        conv.setLastMessagePreview(body != null && body.length() > 200 ? body.substring(0, 200) : body);
        conv.setLastMessageAt(OffsetDateTime.now());
        conversations.save(conv);

        emit("impilo.khuluma.channel.broadcast.v1", conv, senderId);
        return msg;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private ConversationParticipantEntity addOrReactivate(ConversationEntity conv, String actorId,
                                                          String actorType, String displayName, String role) {
        ConversationParticipantEntity p = participants
                .findByConversationIdAndActorId(conv.getConversationId(), actorId)
                .orElseGet(ConversationParticipantEntity::new);
        p.setConversationId(conv.getConversationId());
        p.setTenantId(conv.getTenantId());
        p.setActorId(actorId);
        p.setActorType(actorType != null ? actorType : "PROVIDER");
        if (displayName != null) p.setDisplayName(displayName);
        // Don't demote an existing owner when they re-join as a member.
        if (p.getRole() == null || !"OWNER".equals(p.getRole())) {
            p.setRole(role);
        }
        p.setActive(true);
        p.setLeftAt(null);
        return participants.save(p);
    }

    private ConversationEntity requireConversation(UUID tenantId, UUID conversationId) {
        return conversations.findByConversationIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Channel not found: " + conversationId));
    }

    private void emit(String eventType, ConversationEntity conv, String actorId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", conv.getConversationId().toString());
        payload.put("conversationType", conv.getConversationType());
        payload.put("scopeType", conv.getScopeType());
        payload.put("scopeRef", conv.getScopeRef() != null ? conv.getScopeRef().toString() : null);
        payload.put("actorId", actorId);
        outbox.append(eventType, AGGREGATE, conv.getConversationId().toString(), conv.getTenantId(),
                "national-spine", UUID.randomUUID().toString(),
                eventType + "-" + conv.getConversationId() + "-" + UUID.randomUUID(),
                payload, conv.getTenantId() + ":" + conv.getConversationId(),
                conv.getConversationId().toString(), "CONVERSATION");
    }

    private static String upper(String value, Set<String> allowed, String fallback) {
        if (value == null) return fallback;
        String u = value.trim().toUpperCase();
        return allowed.contains(u) ? u : fallback;
    }
}
