package zw.gov.mohcc.impilo.channels.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.channels.api.dto.CreateMessageRequest;
import zw.gov.mohcc.impilo.channels.api.dto.InboundMessageRequest;
import zw.gov.mohcc.impilo.channels.api.dto.MessageResponse;
import zw.gov.mohcc.impilo.channels.domain.ChannelMessageEntity;
import zw.gov.mohcc.impilo.channels.domain.ChannelSessionEntity;
import zw.gov.mohcc.impilo.channels.repository.ChannelMessageRepository;
import zw.gov.mohcc.impilo.channels.repository.ChannelSessionRepository;
import zw.gov.mohcc.impilo.companion.context.RequestContext;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class MessageService {

    /**
     * Delivery status for an outbound message this service has recorded but not delivered.
     * It is the terminal status here: advancing it to SENT/DELIVERED is a gateway adapter's
     * job, and no such adapter exists yet.
     */
    static final String OUTBOUND_QUEUED_STATUS = "QUEUED";

    private final ChannelMessageRepository messageRepository;
    private final ChannelSessionRepository sessionRepository;
    private final OutboxService outboxService;

    public MessageService(ChannelMessageRepository messageRepository,
                          ChannelSessionRepository sessionRepository,
                          OutboxService outboxService) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public MessageResponse sendOutbound(CreateMessageRequest request, RequestContext ctx,
                                        String idempotencyKey) {
        ChannelSessionEntity session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new SessionService.SessionNotFoundException(request.sessionId()));

        ChannelMessageEntity message = new ChannelMessageEntity(
                session.getId(), ctx.tenantId(), "OUTBOUND",
                session.getChannelType(), request.contentType(), request.payloadJson());
        // QUEUED, not SENT. There is no gateway adapter in this service for any channel type,
        // so persisting the row is the whole of what happens — nothing leaves the building.
        // "SENT" with a deliveredAt stamp claimed an external delivery that never occurred and
        // that nothing could later correct, because no adapter exists to report back.
        // Same honesty rule NotifyOnlyService already applies (QUEUED vs DISPATCHED).
        // deliveredAt stays null: only a real delivery may set it.
        message.setDeliveryStatus(OUTBOUND_QUEUED_STATUS);

        messageRepository.save(message);

        session.setLastActivity(OffsetDateTime.now());
        sessionRepository.save(session);

        outboxService.persistEvent(ctx,
                "impilo.channels.message.outbound.v1",
                "ChannelMessage", message.getId().toString(),
                "ChannelMessage", message.getId().toString(),
                idempotencyKey + ":message.outbound",
                Map.of(
                        "message_id", message.getId().toString(),
                        "session_id", session.getId().toString(),
                        "direction", "OUTBOUND",
                        "channel_type", session.getChannelType(),
                        "content_type", message.getContentType(),
                        "delivery_status", message.getDeliveryStatus()
                ));

        outboxService.persistEvent(ctx,
                "impilo.channels.delivery.updated.v1",
                "ChannelMessage", message.getId().toString(),
                "ChannelMessage", message.getId().toString(),
                idempotencyKey + ":delivery.updated",
                Map.of(
                        "message_id", message.getId().toString(),
                        "session_id", session.getId().toString(),
                        "delivery_status", OUTBOUND_QUEUED_STATUS,
                        "channel_type", session.getChannelType()
                ));

        return toResponse(message);
    }

    @Transactional
    public MessageResponse receiveInbound(InboundMessageRequest request, RequestContext ctx,
                                          String idempotencyKey) {
        ChannelSessionEntity session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new SessionService.SessionNotFoundException(request.sessionId()));

        ChannelMessageEntity message = new ChannelMessageEntity(
                session.getId(), ctx.tenantId(), "INBOUND",
                request.channelType(), request.contentType(), request.payloadJson());
        message.setSenderId(request.senderId());
        message.setDeliveryStatus("DELIVERED");
        message.setDeliveredAt(OffsetDateTime.now());

        messageRepository.save(message);

        session.setLastActivity(OffsetDateTime.now());
        sessionRepository.save(session);

        outboxService.persistEvent(ctx,
                "impilo.channels.message.inbound.v1",
                "ChannelMessage", message.getId().toString(),
                "ChannelMessage", message.getId().toString(),
                idempotencyKey + ":message.inbound",
                Map.of(
                        "message_id", message.getId().toString(),
                        "session_id", session.getId().toString(),
                        "direction", "INBOUND",
                        "channel_type", request.channelType(),
                        "content_type", message.getContentType(),
                        "sender_id", message.getSenderId() != null ? message.getSenderId() : ""
                ));

        outboxService.persistEvent(ctx,
                "impilo.channels.delivery.updated.v1",
                "ChannelMessage", message.getId().toString(),
                "ChannelMessage", message.getId().toString(),
                idempotencyKey + ":delivery.updated",
                Map.of(
                        "message_id", message.getId().toString(),
                        "session_id", session.getId().toString(),
                        "delivery_status", "DELIVERED",
                        "channel_type", request.channelType()
                ));

        return toResponse(message);
    }

    private MessageResponse toResponse(ChannelMessageEntity entity) {
        return new MessageResponse(
                entity.getId(),
                entity.getSessionId(),
                entity.getDirection(),
                entity.getChannelType(),
                entity.getContentType(),
                entity.getDeliveryStatus(),
                entity.getCreatedAt()
        );
    }
}
