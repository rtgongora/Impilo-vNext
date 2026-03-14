package zw.gov.mohcc.impilo.integration.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.integration.api.dto.MappingTemplateRequest;
import zw.gov.mohcc.impilo.integration.api.dto.MappingTemplateResponse;
import zw.gov.mohcc.impilo.integration.domain.MappingTemplateEntity;
import zw.gov.mohcc.impilo.integration.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.integration.repository.MappingTemplateRepository;
import zw.gov.mohcc.impilo.integration.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.EventEnvelope;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MappingTemplateService {

    private static final Logger log = LoggerFactory.getLogger(MappingTemplateService.class);
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final MappingTemplateRepository templateRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public MappingTemplateService(MappingTemplateRepository templateRepository,
                                  OutboxEventRepository outboxRepository,
                                  ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MappingTemplateResponse createTemplate(MappingTemplateRequest request, RequestContext ctx) {
        MappingTemplateEntity entity = new MappingTemplateEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(request.name());
        entity.setTenantId(ctx.tenantId());
        entity.setPodId(ctx.podId());
        entity.setSourceFormat(request.sourceFormat());
        entity.setTargetFormat(request.targetFormat());
        entity.setMappingRulesJson(request.mappingRulesJson());
        entity.setActive(request.active() != null ? request.active() : true);

        entity = templateRepository.save(entity);

        log.info("Created mapping template: id={}, name={}, tenant={}",
                entity.getId(), entity.getName(), entity.getTenantId());

        String idempotencyKey = "mapping-template-create-" + entity.getId();
        persistOutboxEvent(
                "impilo.integration.mapping-template.created.v1",
                "MappingTemplate",
                entity.getId(),
                Map.of(
                        "templateId", entity.getId(),
                        "name", entity.getName(),
                        "active", entity.isActive()
                ),
                idempotencyKey,
                ctx
        );

        return toResponse(entity);
    }

    public List<MappingTemplateResponse> listTemplates(String tenantId) {
        return templateRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Applies mapping template rules to a JSON body.
     * The mapping rules JSON is expected to be an object with field name mappings
     * (old field name -> new field name).
     */
    public String applyMapping(String templateId, String body) {
        if (templateId == null || body == null || body.isBlank()) {
            return body;
        }

        return templateRepository.findById(templateId)
                .filter(MappingTemplateEntity::isActive)
                .map(template -> applyMappingRules(template, body))
                .orElse(body);
    }

    private String applyMappingRules(MappingTemplateEntity template, String body) {
        String rulesJson = template.getMappingRulesJson();
        if (rulesJson == null || rulesJson.isBlank()) {
            return body;
        }

        try {
            Map<String, String> rules = objectMapper.readValue(rulesJson, MAP_TYPE);
            if (rules == null || rules.isEmpty()) {
                return body;
            }

            JsonNode tree = objectMapper.readTree(body);
            if (!tree.isObject()) {
                return body;
            }

            ObjectNode node = (ObjectNode) tree;
            for (Map.Entry<String, String> rule : rules.entrySet()) {
                JsonNode value = node.remove(rule.getKey());
                if (value != null) {
                    node.set(rule.getValue(), value);
                }
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("Failed to apply mapping template {} to body, returning original",
                    template.getId(), e);
            return body;
        }
    }

    MappingTemplateResponse toResponse(MappingTemplateEntity entity) {
        return new MappingTemplateResponse(
                entity.getId(),
                entity.getName(),
                entity.getTenantId(),
                entity.getPodId(),
                entity.getSourceFormat(),
                entity.getTargetFormat(),
                entity.getMappingRulesJson(),
                entity.isActive(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void persistOutboxEvent(String eventType, String subjectType, String subjectId,
                                    Map<String, Object> payload, String idempotencyKey,
                                    RequestContext ctx) {
        try {
            EventEnvelope envelope = EventEnvelope.builder()
                    .eventType(eventType)
                    .schemaVersion(1)
                    .correlationId(ctx.correlationId())
                    .causationId(ctx.requestId())
                    .idempotencyKey(idempotencyKey)
                    .producer("integration-hub")
                    .tenantId(ctx.tenantId())
                    .podId(ctx.podId())
                    .occurredAt(OffsetDateTime.now())
                    .subjectType(subjectType)
                    .subjectId(subjectId)
                    .payload(payload)
                    .build();

            OutboxEventEntity outbox = new OutboxEventEntity();
            outbox.setTenantId(ctx.tenantId());
            outbox.setPodId(ctx.podId());
            outbox.setCorrelationId(ctx.correlationId());
            outbox.setIdempotencyKey(idempotencyKey);
            outbox.setEventType(eventType);
            outbox.setSchemaVersion(1);
            outbox.setOccurredAt(OffsetDateTime.now());
            outbox.setPayloadJson(objectMapper.writeValueAsString(envelope));

            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to persist outbox event: {}", eventType, e);
        }
    }
}
