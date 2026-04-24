package zw.gov.mohcc.impilo.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.notification.api.dto.NotificationResponse;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyRequest;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyResponse;
import zw.gov.mohcc.impilo.notification.domain.NotificationEntity;
import zw.gov.mohcc.impilo.notification.domain.NotificationStatus;
import zw.gov.mohcc.impilo.notification.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.notification.repository.NotificationRepository;
import zw.gov.mohcc.impilo.notification.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    private final NotificationRepository notificationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public NotifyService(NotificationRepository notificationRepository,
                         OutboxEventRepository outboxEventRepository,
                         ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NotifyResponse enqueue(NotifyRequest request, RequestContext ctx) {
        NotificationEntity entity = new NotificationEntity();
        entity.setTemplateKey(request.templateKey());
        entity.setChannel(request.channel());
        entity.setToAddr(request.recipient());
        entity.setVarsJson(serializeVariables(request.variables()));
        entity.setStatus(NotificationStatus.PENDING);
        entity.setTenantId(ctx.tenantId());
        entity.setPodId(ctx.podId());

        entity = notificationRepository.save(entity);
        log.info("Enqueued notification id={} channel={} to={} tenant={}", entity.getId(), entity.getChannel(), entity.getToAddr(), ctx.tenantId());

        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(ctx.tenantId());
        outbox.setPodId(ctx.podId());
        outbox.setCorrelationId(ctx.correlationId());
        outbox.setEventType("impilo.notify.notification.enqueued.v1");
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());

        Map<String, Object> payload = new HashMap<>();
        payload.put("notificationId", entity.getId());
        payload.put("channel", entity.getChannel());
        payload.put("to", entity.getToAddr());
        payload.put("templateKey", entity.getTemplateKey());
        payload.put("status", entity.getStatus().name());
        outbox.setPayloadJson(serializePayload(payload));
        outboxEventRepository.save(outbox);

        return new NotifyResponse(entity.getId(), entity.getStatus().name(), entity.getChannel(), entity.getToAddr());
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listNotifications(String tenantId, Pageable pageable) {
        return notificationRepository.findByTenantId(tenantId, pageable)
                .map(this::toResponse);
    }

    private NotificationResponse toResponse(NotificationEntity entity) {
        return new NotificationResponse(
                entity.getId(),
                entity.getTemplateKey(),
                entity.getChannel(),
                entity.getToAddr(),
                entity.getStatus().name(),
                entity.getAttempts(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getSentAt()
        );
    }

    private String serializeVariables(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification variables", e);
            throw new RuntimeException("Failed to serialize notification variables", e);
        }
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload", e);
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
    }
}
