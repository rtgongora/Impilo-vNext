package zw.gov.mohcc.impilo.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyRequest;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyResponse;
import zw.gov.mohcc.impilo.notification.domain.NotificationRequestEntity;
import zw.gov.mohcc.impilo.notification.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.notification.repository.NotificationRequestRepository;
import zw.gov.mohcc.impilo.notification.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    private final NotificationRequestRepository notificationRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public NotifyService(NotificationRequestRepository notificationRequestRepository,
                         OutboxEventRepository outboxEventRepository,
                         ObjectMapper objectMapper) {
        this.notificationRequestRepository = notificationRequestRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NotifyResponse send(NotifyRequest request, RequestContext ctx) {
        NotificationRequestEntity entity = new NotificationRequestEntity();
        entity.setChannel(request.channel());
        entity.setRecipient(request.recipient());
        entity.setTemplateId(request.templateId());
        entity.setVariablesJson(serializeVariables(request.variables()));
        entity.setStatus("PENDING");
        entity.setTenantId(ctx.tenantId());
        entity.setPodId(ctx.podId());

        entity = notificationRequestRepository.save(entity);
        log.info("Created notification request id={} channel={} tenant={}", entity.getId(), entity.getChannel(), ctx.tenantId());

        // Publish outbox event
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(ctx.tenantId());
        outbox.setPodId(ctx.podId());
        outbox.setCorrelationId(ctx.correlationId());
        outbox.setEventType("notification.request.created");
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());

        Map<String, Object> payload = new HashMap<>();
        payload.put("notificationId", entity.getId());
        payload.put("channel", entity.getChannel());
        payload.put("recipient", entity.getRecipient());
        payload.put("templateId", entity.getTemplateId());
        payload.put("status", entity.getStatus());
        outbox.setPayloadJson(serializePayload(payload));
        outboxEventRepository.save(outbox);

        return new NotifyResponse(entity.getId(), entity.getStatus());
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
