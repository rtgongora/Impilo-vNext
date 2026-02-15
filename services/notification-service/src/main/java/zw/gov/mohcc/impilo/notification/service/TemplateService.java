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
import java.util.Optional;

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
    public TemplateResponse createTemplate(TemplateRequest request, RequestContext ctx) {
        TemplateEntity entity = new TemplateEntity();
        entity.setKey(request.key());
        entity.setChannel(request.channel());
        entity.setName(request.key());
        entity.setSubject(request.subject());
        entity.setContent(request.body());
        entity.setEnabled(request.isEnabledOrDefault());
        entity.setTenantId(ctx.tenantId());
        entity.setPodId(ctx.podId());

        entity = templateRepository.save(entity);
        log.info("Created template id={} key={} channel={} tenant={}", entity.getId(), entity.getKey(), entity.getChannel(), ctx.tenantId());

        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(ctx.tenantId());
        outbox.setPodId(ctx.podId());
        outbox.setCorrelationId(ctx.correlationId());
        outbox.setEventType("impilo.notify.template.created.v1");
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(serializePayload(Map.of(
                "templateId", entity.getId(),
                "key", entity.getKey(),
                "channel", entity.getChannel(),
                "enabled", entity.isEnabled()
        )));
        outboxEventRepository.save(outbox);

        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public Optional<TemplateResponse> getByKey(String key) {
        return templateRepository.findByKey(key).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listTemplates(String tenantId) {
        return templateRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    public String renderBody(TemplateEntity template, Map<String, String> variables) {
        return substituteVariables(template.getContent(), variables);
    }

    public String renderSubject(TemplateEntity template, Map<String, String> variables) {
        if (template.getSubject() == null) {
            return null;
        }
        return substituteVariables(template.getSubject(), variables);
    }

    private String substituteVariables(String text, Map<String, String> variables) {
        if (text == null || variables == null || variables.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private TemplateResponse toResponse(TemplateEntity entity) {
        return new TemplateResponse(
                entity.getId(),
                entity.getKey(),
                entity.getChannel(),
                entity.getSubject(),
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
