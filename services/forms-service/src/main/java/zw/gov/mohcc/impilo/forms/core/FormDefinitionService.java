package zw.gov.mohcc.impilo.forms.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.forms.api.dto.CreateFormRequest;
import zw.gov.mohcc.impilo.forms.api.dto.FormDefinitionResponse;
import zw.gov.mohcc.impilo.forms.api.dto.UpdateFormRequest;
import zw.gov.mohcc.impilo.forms.domain.FormDefinitionEntity;
import zw.gov.mohcc.impilo.forms.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.forms.repository.FormDefinitionRepository;
import zw.gov.mohcc.impilo.forms.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class FormDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(FormDefinitionService.class);

    private final FormDefinitionRepository formRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public FormDefinitionService(FormDefinitionRepository formRepository,
                                 OutboxEventRepository outboxRepository,
                                 ObjectMapper objectMapper) {
        this.formRepository = formRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FormDefinitionResponse create(CreateFormRequest request, RequestContext ctx) {
        FormDefinitionEntity entity = new FormDefinitionEntity();
        entity.setTenantId(ctx.tenantId());
        entity.setPodId(ctx.podId());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setCategory(request.category());
        entity.setCreatedBy(ctx.requestId());

        FormDefinitionEntity saved = formRepository.save(entity);

        publishOutbox(ctx, "impilo.forms.form.created.v1", Map.of(
                "formId", saved.getId(),
                "code", saved.getCode(),
                "name", saved.getName(),
                "status", saved.getStatus()
        ));

        log.info("Form created: id={}, code={}", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public FormDefinitionResponse getById(String id) {
        return formRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new FormNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<FormDefinitionResponse> listByTenant(String tenantId) {
        return formRepository.findByTenantId(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FormDefinitionResponse update(String id, UpdateFormRequest request, RequestContext ctx) {
        FormDefinitionEntity entity = formRepository.findById(id)
                .orElseThrow(() -> new FormNotFoundException(id));

        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setCategory(request.category());

        FormDefinitionEntity saved = formRepository.save(entity);

        publishOutbox(ctx, "impilo.forms.form.updated.v1", Map.of(
                "formId", saved.getId(),
                "code", saved.getCode(),
                "name", saved.getName(),
                "status", saved.getStatus()
        ));

        log.info("Form updated: id={}, code={}", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    @Transactional
    public void markPublished(String id) {
        FormDefinitionEntity entity = formRepository.findById(id)
                .orElseThrow(() -> new FormNotFoundException(id));
        entity.setStatus("PUBLISHED");
        formRepository.save(entity);
    }

    private void publishOutbox(RequestContext ctx, String eventType, Map<String, Object> payload) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(ctx.tenantId());
        outbox.setPodId(ctx.podId());
        outbox.setCorrelationId(ctx.correlationId());
        outbox.setEventType(eventType);
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outboxRepository.save(outbox);
    }

    FormDefinitionResponse toResponse(FormDefinitionEntity entity) {
        return new FormDefinitionResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getStatus(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
