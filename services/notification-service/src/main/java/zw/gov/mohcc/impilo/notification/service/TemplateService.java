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
import zw.gov.mohcc.impilo.notification.api.dto.TemplateVersionRequest;
import zw.gov.mohcc.impilo.notification.api.dto.TemplateVersionResponse;
import zw.gov.mohcc.impilo.notification.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.notification.domain.TemplateEntity;
import zw.gov.mohcc.impilo.notification.domain.TemplateVersionEntity;
import zw.gov.mohcc.impilo.notification.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.notification.repository.TemplateRepository;
import zw.gov.mohcc.impilo.notification.repository.TemplateVersionRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public TemplateService(TemplateRepository templateRepository,
                           TemplateVersionRepository templateVersionRepository,
                           OutboxEventRepository outboxEventRepository,
                           ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.templateVersionRepository = templateVersionRepository;
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

        TemplateVersionEntity initialVersion = new TemplateVersionEntity();
        initialVersion.setTemplateId(entity.getId());
        initialVersion.setVersion(1);
        initialVersion.setContent(entity.getContent());
        initialVersion.setSubject(entity.getSubject());
        initialVersion.setChangelog("Initial version");
        templateVersionRepository.save(initialVersion);

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

    @Transactional
    public TemplateResponse publishTemplate(String templateId, RequestContext ctx) {
        TemplateEntity entity = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        if (!"DRAFT".equals(entity.getStatus())) {
            throw new IllegalStateException("Only DRAFT templates can be published. Current status: " + entity.getStatus());
        }

        entity.setStatus("PUBLISHED");
        entity = templateRepository.save(entity);
        log.info("Published template id={} key={} tenant={}", entity.getId(), entity.getKey(), ctx.tenantId());

        emitTemplateOutboxEvent(entity, "impilo.notify.template.published.v1", ctx);

        return toResponse(entity);
    }

    @Transactional
    public TemplateResponse retireTemplate(String templateId, RequestContext ctx) {
        TemplateEntity entity = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        if (!"PUBLISHED".equals(entity.getStatus())) {
            throw new IllegalStateException("Only PUBLISHED templates can be retired. Current status: " + entity.getStatus());
        }

        entity.setStatus("RETIRED");
        entity = templateRepository.save(entity);
        log.info("Retired template id={} key={} tenant={}", entity.getId(), entity.getKey(), ctx.tenantId());

        emitTemplateOutboxEvent(entity, "impilo.notify.template.retired.v1", ctx);

        return toResponse(entity);
    }

    @Transactional
    public TemplateVersionResponse createVersion(String templateId, TemplateVersionRequest request, RequestContext ctx) {
        TemplateEntity template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        int nextVersion = template.getCurrentVersion() + 1;

        TemplateVersionEntity version = new TemplateVersionEntity();
        version.setTemplateId(templateId);
        version.setVersion(nextVersion);
        version.setContent(request.content());
        version.setSubject(request.subject());
        version.setChangelog(request.changelog());
        version = templateVersionRepository.save(version);

        template.setCurrentVersion(nextVersion);
        template.setContent(request.content());
        if (request.subject() != null) {
            template.setSubject(request.subject());
        }
        templateRepository.save(template);

        log.info("Created version {} for template id={} key={} tenant={}", nextVersion, templateId, template.getKey(), ctx.tenantId());

        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(ctx.tenantId());
        outbox.setPodId(ctx.podId());
        outbox.setCorrelationId(ctx.correlationId());
        outbox.setEventType("impilo.notify.template.version_created.v1");
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(serializePayload(Map.of(
                "templateId", templateId,
                "key", template.getKey(),
                "version", nextVersion,
                "versionId", version.getId()
        )));
        outboxEventRepository.save(outbox);

        return toVersionResponse(version);
    }

    @Transactional(readOnly = true)
    public List<TemplateVersionResponse> listVersions(String templateId) {
        templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        return templateVersionRepository.findByTemplateIdOrderByVersionDesc(templateId).stream()
                .map(this::toVersionResponse)
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

    private void emitTemplateOutboxEvent(TemplateEntity entity, String eventType, RequestContext ctx) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(ctx.tenantId());
        outbox.setPodId(ctx.podId());
        outbox.setCorrelationId(ctx.correlationId());
        outbox.setEventType(eventType);
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(serializePayload(Map.of(
                "templateId", entity.getId(),
                "key", entity.getKey(),
                "channel", entity.getChannel(),
                "status", entity.getStatus()
        )));
        outboxEventRepository.save(outbox);
    }

    private TemplateResponse toResponse(TemplateEntity entity) {
        return new TemplateResponse(
                entity.getId(),
                entity.getKey(),
                entity.getChannel(),
                entity.getSubject(),
                entity.getContent(),
                entity.isEnabled(),
                entity.getStatus(),
                entity.getCurrentVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private TemplateVersionResponse toVersionResponse(TemplateVersionEntity entity) {
        return new TemplateVersionResponse(
                entity.getId(),
                entity.getTemplateId(),
                entity.getVersion(),
                entity.getContent(),
                entity.getSubject(),
                entity.getChangelog(),
                entity.getCreatedAt()
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
