package zw.gov.mohcc.impilo.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.notification.api.dto.TemplateRequest;
import zw.gov.mohcc.impilo.notification.api.dto.TemplateResponse;
import zw.gov.mohcc.impilo.notification.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.notification.domain.TemplateEntity;
import zw.gov.mohcc.impilo.notification.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.notification.repository.TemplateRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    private final TemplateRepository templateRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public TemplateService(TemplateRepository templateRepository,
                           OutboxEventRepository outboxEventRepository,
                           ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TemplateResponse upsertTemplate(TemplateRequest request, RequestContext ctx) {
        TemplateEntity entity = new TemplateEntity();
        entity.setChannel(request.channel());
        entity.setName(request.name());
        entity.setContent(request.content());
        entity.setEnabled(request.isEnabledOrDefault());
        entity.setTenantId(ctx.tenantId());
        entity.setPodId(ctx.podId());

        entity = templateRepository.save(entity);
        log.info("Upserted template id={} channel={} tenant={}", entity.getId(), entity.getChannel(), ctx.tenantId());

        // Publish outbox event
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(ctx.tenantId());
        outbox.setPodId(ctx.podId());
        outbox.setCorrelationId(ctx.correlationId());
        outbox.setEventType("notification.template.upserted");
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(serializePayload(Map.of(
                "templateId", entity.getId(),
                "channel", entity.getChannel(),
                "name", entity.getName(),
                "enabled", entity.isEnabled()
        )));
        outboxEventRepository.save(outbox);

        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listTemplates(String tenantId) {
        return templateRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    private TemplateResponse toResponse(TemplateEntity entity) {
        return new TemplateResponse(
                entity.getId(),
                entity.getChannel(),
                entity.getName(),
                entity.getContent(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
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
