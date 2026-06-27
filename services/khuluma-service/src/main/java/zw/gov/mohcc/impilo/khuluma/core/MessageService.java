package zw.gov.mohcc.impilo.khuluma.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationEntity;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationParticipantEntity;
import zw.gov.mohcc.impilo.khuluma.domain.MessageEntity;
import zw.gov.mohcc.impilo.khuluma.domain.MessageReceiptEntity;
import zw.gov.mohcc.impilo.khuluma.repository.ConversationParticipantRepository;
import zw.gov.mohcc.impilo.khuluma.repository.ConversationRepository;
import zw.gov.mohcc.impilo.khuluma.repository.MessageReceiptRepository;
import zw.gov.mohcc.impilo.khuluma.repository.MessageRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Owns message send/list and read-receipt tracking. Sending updates the conversation's
 * last-message denormalisation, appends a domain/audit event, and pushes a realtime
 * {@code message.created} to the conversation channel. Membership is enforced: only an
 * active participant may post.
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final String AGGREGATE = "Message";
    private static final int PREVIEW_MAX = 500;

    private final MessageRepository messages;
    private final MessageReceiptRepository receipts;
    private final ConversationRepository conversations;
    private final ConversationParticipantRepository participants;
    private final OutboxAppender outbox;
    private final RealtimeDispatcher realtime;

    public MessageService(MessageRepository messages,
                          MessageReceiptRepository receipts,
                          ConversationRepository conversations,
                          ConversationParticipantRepository participants,
                          OutboxAppender outbox,
                          RealtimeDispatcher realtime) {
        this.messages = messages;
        this.receipts = receipts;
        this.conversations = conversations;
        this.participants = participants;
        this.outbox = outbox;
        this.realtime = realtime;
    }

    @Transactional
    public MessageEntity send(ActorContext ctx, UUID conversationId, String body,
                              String contentType, String clientMessageId, String metadataJson) {
        ConversationEntity conv = conversations.findByConversationIdAndTenantId(conversationId, ctx.tenantId())
                .orElseThrow(() -> new NoSuchElementException("Conversation not found: " + conversationId));

        if (!participants.existsByConversationIdAndActorIdAndActiveTrue(conversationId, ctx.actorId())) {
            throw new SecurityException("Actor is not an active participant of conversation " + conversationId);
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Message body must not be blank");
        }

        // Idempotent re-send: same (conversation, clientMessageId) returns the original.
        if (clientMessageId != null && !clientMessageId.isBlank()) {
            var existing = messages.findByConversationIdAndClientMessageId(conversationId, clientMessageId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        MessageEntity msg = new MessageEntity();
        msg.setConversationId(conversationId);
        msg.setTenantId(ctx.tenantId());
        msg.setSenderId(ctx.actorId());
        msg.setSenderType(ctx.actorType());
        msg.setContentType(normalizeContentType(contentType));
        msg.setBody(body);
        msg.setClientMessageId(clientMessageId);
        msg.setMetadataJson(metadataJson);
        messages.save(msg);

        // Denormalise last-message onto the conversation for inbox ordering.
        conv.setLastMessageId(msg.getMessageId());
        conv.setLastMessagePreview(preview(body));
        conv.setLastMessageAt(msg.getSentAt());
        conv.setUpdatedAt(OffsetDateTime.now());
        conversations.save(conv);

        Map<String, Object> payload = messagePayload(msg);
        outbox.append("impilo.khuluma.message.sent.v1", AGGREGATE, msg.getMessageId().toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "msg-sent-" + msg.getMessageId(), payload,
                conversationId.toString(), conversationId.toString(), "Conversation");

        realtime.publish(ctx.tenantId(), "conversation:" + conversationId, "message.created", payload);

        log.info("Message sent [conversation={}, message={}, sender={}]",
                conversationId, msg.getMessageId(), ctx.actorId());
        return msg;
    }

    @Transactional(readOnly = true)
    public List<MessageEntity> list(ActorContext ctx, UUID conversationId) {
        conversations.findByConversationIdAndTenantId(conversationId, ctx.tenantId())
                .orElseThrow(() -> new NoSuchElementException("Conversation not found: " + conversationId));
        if (!participants.existsByConversationIdAndActorIdAndActiveTrue(conversationId, ctx.actorId())) {
            throw new SecurityException("Actor is not an active participant of conversation " + conversationId);
        }
        return messages.findByConversationIdOrderBySentAtAsc(conversationId);
    }

    /**
     * Mark the conversation read up to {@code upToMessageId} for the acting participant.
     * Advances their read pointer and records a READ receipt; pushes {@code receipt.read}.
     */
    @Transactional
    public void markRead(ActorContext ctx, UUID conversationId, UUID upToMessageId) {
        ConversationParticipantEntity participant = participants
                .findByConversationIdAndActorId(conversationId, ctx.actorId())
                .orElseThrow(() -> new NoSuchElementException("Not a participant of " + conversationId));

        MessageEntity message = messages.findById(upToMessageId)
                .filter(m -> m.getConversationId().equals(conversationId))
                .orElseThrow(() -> new NoSuchElementException("Message not in conversation: " + upToMessageId));

        participant.setLastReadAt(OffsetDateTime.now());
        participant.setLastReadMessageId(upToMessageId);
        participants.save(participant);

        if (!receipts.existsByMessageIdAndActorIdAndReceiptState(upToMessageId, ctx.actorId(), "READ")) {
            MessageReceiptEntity receipt = new MessageReceiptEntity();
            receipt.setMessageId(upToMessageId);
            receipt.setConversationId(conversationId);
            receipt.setTenantId(ctx.tenantId());
            receipt.setActorId(ctx.actorId());
            receipt.setReceiptState("READ");
            receipts.save(receipt);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", conversationId.toString());
        payload.put("message_id", upToMessageId.toString());
        payload.put("actor_id", ctx.actorId());
        payload.put("state", "READ");
        outbox.append("impilo.khuluma.message.read.v1", AGGREGATE, upToMessageId.toString(),
                ctx.tenantId(), ctx.podId(), ctx.correlationId(),
                "msg-read-" + upToMessageId + "-" + ctx.actorId(), payload,
                conversationId.toString(), conversationId.toString(), "Conversation");

        realtime.publish(ctx.tenantId(), "conversation:" + conversationId, "receipt.read", payload);
    }

    @Transactional(readOnly = true)
    public List<MessageReceiptEntity> receiptsFor(UUID messageId) {
        return receipts.findByMessageId(messageId);
    }

    private static Map<String, Object> messagePayload(MessageEntity msg) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message_id", msg.getMessageId().toString());
        payload.put("conversation_id", msg.getConversationId().toString());
        payload.put("sender_id", msg.getSenderId());
        payload.put("sender_type", msg.getSenderType());
        payload.put("content_type", msg.getContentType());
        payload.put("body", msg.getBody());
        payload.put("sent_at", msg.getSentAt().toString());
        return payload;
    }

    private static String preview(String body) {
        String trimmed = body.strip();
        return trimmed.length() <= PREVIEW_MAX ? trimmed : trimmed.substring(0, PREVIEW_MAX);
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) return "TEXT";
        String upper = contentType.trim().toUpperCase();
        return switch (upper) {
            case "TEXT", "SYSTEM", "ATTACHMENT" -> upper;
            default -> "TEXT";
        };
    }
}
