package zw.gov.mohcc.impilo.search.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.search.api.IndexRequest;
import zw.gov.mohcc.impilo.search.api.IndexResponse;
import zw.gov.mohcc.impilo.search.api.SearchResponse;
import zw.gov.mohcc.impilo.search.persistence.entity.OutboxEventEntity;
import zw.gov.mohcc.impilo.search.persistence.entity.SearchIndexEntity;
import zw.gov.mohcc.impilo.search.persistence.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.search.persistence.repository.SearchIndexRepository;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);

    private final SearchIndexRepository searchIndexRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public SearchIndexService(SearchIndexRepository searchIndexRepository,
                              OutboxEventRepository outboxEventRepository,
                              ObjectMapper objectMapper) {
        this.searchIndexRepository = searchIndexRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IndexResponse indexEntity(IndexRequest request, RequestContext ctx) {
        String tenantId = ctx.tenantId();
        String podId = ctx.podId();
        String contentJsonStr = serializeJson(request.contentJson());
        String searchableText = flattenJsonValues(request.contentJson());
        String tags = request.tags() != null ? String.join(",", request.tags()) : null;

        Optional<SearchIndexEntity> existing = searchIndexRepository
                .findByEntityTypeAndEntityIdAndTenantId(request.entityType(), request.entityId(), tenantId);

        SearchIndexEntity entity;
        String eventType;

        if (existing.isPresent()) {
            entity = existing.get();
            entity.setContentJson(contentJsonStr);
            entity.setSearchableText(searchableText);
            entity.setTags(tags);
            entity.setPodId(podId);
            entity.setIndexedAt(OffsetDateTime.now());
            entity.setSourceEvent(request.sourceEvent());
            eventType = "impilo.search.index.updated.v1";
            log.info("Updating search index entry [entityType={}, entityId={}, tenantId={}]",
                    request.entityType(), request.entityId(), tenantId);
        } else {
            entity = new SearchIndexEntity();
            entity.setEntityType(request.entityType());
            entity.setEntityId(request.entityId());
            entity.setTenantId(tenantId);
            entity.setPodId(podId);
            entity.setContentJson(contentJsonStr);
            entity.setSearchableText(searchableText);
            entity.setTags(tags);
            entity.setSourceEvent(request.sourceEvent());
            eventType = "impilo.search.index.created.v1";
            log.info("Creating search index entry [entityType={}, entityId={}, tenantId={}]",
                    request.entityType(), request.entityId(), tenantId);
        }

        entity = searchIndexRepository.save(entity);
        emitOutboxEvent(eventType, entity, ctx);

        return toResponse(entity);
    }

    @Transactional
    public void removeEntity(String entityType, String entityId, RequestContext ctx) {
        String tenantId = ctx.tenantId();
        Optional<SearchIndexEntity> existing = searchIndexRepository
                .findByEntityTypeAndEntityIdAndTenantId(entityType, entityId, tenantId);

        if (existing.isPresent()) {
            SearchIndexEntity entity = existing.get();
            searchIndexRepository.delete(entity);
            emitOutboxEvent("impilo.search.index.removed.v1", entity, ctx);
            log.info("Removed search index entry [entityType={}, entityId={}, tenantId={}]",
                    entityType, entityId, tenantId);
        } else {
            log.warn("Search index entry not found for removal [entityType={}, entityId={}, tenantId={}]",
                    entityType, entityId, tenantId);
        }
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String query, String tenantId, String entityType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "indexedAt"));
        Page<SearchIndexEntity> results;

        if (entityType != null && !entityType.isBlank()) {
            results = searchIndexRepository.searchByTextAndEntityType(tenantId, query, entityType, pageable);
        } else {
            results = searchIndexRepository.searchByText(tenantId, query, pageable);
        }

        return toSearchResponse(results, page, size);
    }

    @Transactional(readOnly = true)
    public SearchResponse listByType(String entityType, String tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "indexedAt"));
        Page<SearchIndexEntity> results = searchIndexRepository
                .findByTenantIdAndEntityType(tenantId, entityType, pageable);

        return toSearchResponse(results, page, size);
    }

    private void emitOutboxEvent(String eventType, SearchIndexEntity entity, RequestContext ctx) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(ctx.tenantId());
        outbox.setPodId(ctx.podId() != null ? ctx.podId() : "unknown");
        outbox.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId() : UUID.randomUUID().toString());
        outbox.setEventType(eventType);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(buildEventPayload(entity));
        outboxEventRepository.save(outbox);
    }

    private String buildEventPayload(SearchIndexEntity entity) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "id", entity.getId(),
                    "entityType", entity.getEntityType(),
                    "entityId", entity.getEntityId(),
                    "tenantId", entity.getTenantId(),
                    "indexedAt", entity.getIndexedAt().toString()
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload", e);
            return "{}";
        }
    }

    private IndexResponse toResponse(SearchIndexEntity entity) {
        Map<String, Object> contentMap;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(entity.getContentJson(), Map.class);
            contentMap = parsed;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize content JSON for entity {}", entity.getId(), e);
            contentMap = Map.of();
        }

        List<String> tagsList = entity.getTags() != null && !entity.getTags().isBlank()
                ? Arrays.asList(entity.getTags().split(","))
                : List.of();

        return new IndexResponse(
                entity.getId(),
                entity.getEntityType(),
                entity.getEntityId(),
                contentMap,
                tagsList,
                entity.getSearchableText(),
                entity.getIndexedAt()
        );
    }

    private SearchResponse toSearchResponse(Page<SearchIndexEntity> pageResult, int page, int size) {
        List<IndexResponse> results = pageResult.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return new SearchResponse(results, page, size,
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    private String serializeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize content JSON", e);
            return "{}";
        }
    }

    private String flattenJsonValues(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        flattenRecursive(map, sb);
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private void flattenRecursive(Object obj, StringBuilder sb) {
        if (obj == null) {
            return;
        }
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            for (Object value : map.values()) {
                flattenRecursive(value, sb);
            }
        } else if (obj instanceof Iterable) {
            for (Object item : (Iterable<?>) obj) {
                flattenRecursive(item, sb);
            }
        } else {
            sb.append(obj.toString()).append(" ");
        }
    }
}
