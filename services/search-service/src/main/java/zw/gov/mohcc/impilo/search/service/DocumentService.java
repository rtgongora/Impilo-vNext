package zw.gov.mohcc.impilo.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.search.api.dto.DocumentResponse;
import zw.gov.mohcc.impilo.search.api.dto.DocumentUpsertRequest;
import zw.gov.mohcc.impilo.search.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.search.domain.SearchDocumentEntity;
import zw.gov.mohcc.impilo.search.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.search.repository.SearchDocumentRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {

    private final SearchDocumentRepository documentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public DocumentService(SearchDocumentRepository documentRepository,
                           OutboxEventRepository outboxEventRepository,
                           ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DocumentResponse upsert(DocumentUpsertRequest request, RequestContext ctx) {
        SearchDocumentEntity entity = documentRepository
                .findByTenantIdAndIndexIdAndExternalId(ctx.tenantId(), request.indexId(), request.externalId())
                .orElseGet(() -> {
                    SearchDocumentEntity newDoc = new SearchDocumentEntity();
                    newDoc.setId(UUID.randomUUID().toString());
                    newDoc.setIndexId(request.indexId());
                    newDoc.setExternalId(request.externalId());
                    newDoc.setTenantId(ctx.tenantId());
                    newDoc.setPodId(ctx.podId());
                    return newDoc;
                });

        entity.setTitle(request.title());
        entity.setBodyText(request.bodyText());
        entity.setMetadataJson(request.metadata() != null ? toJson(request.metadata()) : null);

        SearchDocumentEntity saved = documentRepository.save(entity);

        publishOutbox(ctx, "impilo.search.document.upserted.v1", Map.of(
                "documentId", saved.getId(),
                "indexId", saved.getIndexId(),
                "externalId", saved.getExternalId()
        ));

        return toResponse(saved);
    }

    @Transactional
    public void delete(String docId, RequestContext ctx) {
        SearchDocumentEntity entity = documentRepository.findById(docId)
                .orElseThrow(() -> new DocumentNotFoundException(docId));

        documentRepository.delete(entity);

        publishOutbox(ctx, "impilo.search.document.deleted.v1", Map.of(
                "documentId", entity.getId(),
                "indexId", entity.getIndexId(),
                "externalId", entity.getExternalId()
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

    private DocumentResponse toResponse(SearchDocumentEntity entity) {
        return new DocumentResponse(
                entity.getId(),
                entity.getIndexId(),
                entity.getExternalId(),
                entity.getTitle(),
                entity.getBodyText(),
                entity.getMetadataJson() != null ? parseJson(entity.getMetadataJson()) : null,
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
