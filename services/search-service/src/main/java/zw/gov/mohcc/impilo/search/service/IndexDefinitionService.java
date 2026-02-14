package zw.gov.mohcc.impilo.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.search.api.dto.IndexDefinitionRequest;
import zw.gov.mohcc.impilo.search.api.dto.IndexDefinitionResponse;
import zw.gov.mohcc.impilo.search.domain.IndexDefinitionEntity;
import zw.gov.mohcc.impilo.search.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.search.repository.IndexDefinitionRepository;
import zw.gov.mohcc.impilo.search.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IndexDefinitionService {

    private final IndexDefinitionRepository indexRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public IndexDefinitionService(IndexDefinitionRepository indexRepository,
                                  OutboxEventRepository outboxEventRepository,
                                  ObjectMapper objectMapper) {
        this.indexRepository = indexRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IndexDefinitionResponse create(IndexDefinitionRequest request, RequestContext ctx) {
        IndexDefinitionEntity entity = new IndexDefinitionEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(request.name());
        entity.setSourceType(request.sourceType());
        entity.setFieldsJson(toJson(request.fields()));
        entity.setEnabled(request.isEnabled());
        entity.setTenantId(ctx.tenantId());
        entity.setPodId(ctx.podId());

        IndexDefinitionEntity saved = indexRepository.save(entity);

        publishOutbox(ctx, "impilo.search.index.created.v1", Map.of(
                "indexId", saved.getId(),
                "name", saved.getName(),
                "sourceType", saved.getSourceType()
        ));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public IndexDefinitionResponse getById(String id) {
        return indexRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new IndexNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<IndexDefinitionResponse> listByTenant(String tenantId) {
        return indexRepository.findByTenantId(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public IndexDefinitionResponse update(String id, IndexDefinitionRequest request, RequestContext ctx) {
        IndexDefinitionEntity entity = indexRepository.findById(id)
                .orElseThrow(() -> new IndexNotFoundException(id));

        entity.setName(request.name());
        entity.setSourceType(request.sourceType());
        entity.setFieldsJson(toJson(request.fields()));
        entity.setEnabled(request.isEnabled());

        IndexDefinitionEntity saved = indexRepository.save(entity);

        publishOutbox(ctx, "impilo.search.index.updated.v1", Map.of(
                "indexId", saved.getId(),
                "name", saved.getName(),
                "sourceType", saved.getSourceType()
        ));

        return toResponse(saved);
    }

    @Transactional
    public void delete(String id, RequestContext ctx) {
        IndexDefinitionEntity entity = indexRepository.findById(id)
                .orElseThrow(() -> new IndexNotFoundException(id));

        indexRepository.delete(entity);

        publishOutbox(ctx, "impilo.search.index.deleted.v1", Map.of(
                "indexId", entity.getId(),
                "name", entity.getName()
        ));
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
        outboxEventRepository.save(outbox);
    }

    private IndexDefinitionResponse toResponse(IndexDefinitionEntity entity) {
        return new IndexDefinitionResponse(
                entity.getId(),
                entity.getName(),
                entity.getSourceType(),
                parseJson(entity.getFieldsJson()),
                entity.isEnabled(),
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

    private Object parseJson(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }
}
