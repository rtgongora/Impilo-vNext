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
import zw.gov.mohcc.impilo.search.embeddings.EmbeddingModel;
import zw.gov.mohcc.impilo.search.embeddings.EmbeddingVectors;
import zw.gov.mohcc.impilo.search.embeddings.SearchEmbeddingsProperties;
import zw.gov.mohcc.impilo.search.embeddings.SearchRankingProperties;
import zw.gov.mohcc.impilo.search.pgvector.PgvectorAnnSupport;
import zw.gov.mohcc.impilo.search.pgvector.SearchPgvectorProperties;
import zw.gov.mohcc.impilo.search.persistence.entity.OutboxEventEntity;
import zw.gov.mohcc.impilo.search.persistence.entity.SearchIndexEntity;
import zw.gov.mohcc.impilo.search.persistence.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.search.persistence.repository.SearchIndexRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final EmbeddingModel embeddingModel;
    private final SearchEmbeddingsProperties embeddingsProperties;
    private final SearchRankingProperties rankingProperties;
    private final PgvectorAnnSupport pgvectorAnnSupport;
    private final SearchPgvectorProperties searchPgvectorProperties;

    public SearchIndexService(SearchIndexRepository searchIndexRepository,
                              OutboxEventRepository outboxEventRepository,
                              ObjectMapper objectMapper,
                              EmbeddingModel embeddingModel,
                              SearchEmbeddingsProperties embeddingsProperties,
                              SearchRankingProperties rankingProperties,
                              PgvectorAnnSupport pgvectorAnnSupport,
                              SearchPgvectorProperties searchPgvectorProperties) {
        this.searchIndexRepository = searchIndexRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.embeddingModel = embeddingModel;
        this.embeddingsProperties = embeddingsProperties;
        this.rankingProperties = rankingProperties;
        this.pgvectorAnnSupport = pgvectorAnnSupport;
        this.searchPgvectorProperties = searchPgvectorProperties;
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

        attachEmbedding(entity, searchableText);

        entity = searchIndexRepository.save(entity);
        pgvectorAnnSupport.syncEmbeddingVector(entity.getId(), EmbeddingVectors.fromJson(entity.getEmbeddingJson()));
        emitOutboxEvent(eventType, entity, ctx);

        return toResponse(entity);
    }

    private void attachEmbedding(SearchIndexEntity entity, String searchableText) {
        try {
            String chunk = EmbeddingVectors.truncate(searchableText, embeddingsProperties.getMaxInputChars());
            float[] vec = embeddingModel.embed(chunk);
            entity.setEmbeddingJson(EmbeddingVectors.toJson(vec));
        } catch (Exception e) {
            log.warn("Skipping embedding for entity [{}, {}]: {}",
                    entity.getEntityType(), entity.getEntityId(), e.getMessage());
        }
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
    public SearchResponse search(String query, String tenantId, String entityType, int page, int size, String rankMode) {
        String mode = rankMode == null ? "" : rankMode.trim();
        boolean semanticOrHybrid = "semantic".equalsIgnoreCase(mode) || "hybrid".equalsIgnoreCase(mode);
        float[] qEmbed = null;
        if (semanticOrHybrid) {
            qEmbed = embeddingModel.embed(EmbeddingVectors.truncate(query, embeddingsProperties.getMaxInputChars()));
        }

        if (semanticOrHybrid && qEmbed != null && pgvectorAnnSupport.isUsable()) {
            float[] qPadded = EmbeddingVectors.padOrTruncate(qEmbed, searchPgvectorProperties.getDimensions());
            if (qPadded != null) {
                int annCap = Math.max(1, searchPgvectorProperties.getAnnCandidateLimit());
                List<String> annIds = pgvectorAnnSupport.annSearchIds(tenantId, entityType, qPadded, annCap, 0);
                if (!annIds.isEmpty()) {
                    return searchViaPgvectorAnn(query, tenantId, entityType, page, size, mode, annIds, annCap);
                }
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "indexedAt"));
        Page<SearchIndexEntity> results;

        if (entityType != null && !entityType.isBlank()) {
            results = searchIndexRepository.searchByTextAndEntityType(tenantId, query, entityType, pageable);
        } else {
            results = searchIndexRepository.searchByText(tenantId, query, pageable);
        }

        return toSearchResponse(query, results, page, size, rankMode);
    }

    private SearchResponse searchViaPgvectorAnn(
            String query,
            String tenantId,
            String entityType,
            int page,
            int size,
            String mode,
            List<String> annIds,
            int annCap) {

        List<String> merged = new ArrayList<>(annIds);
        int mergeCap = Math.max(1, searchPgvectorProperties.getMergeLexicalCap());
        if (searchPgvectorProperties.isMergeLexicalCandidates()) {
            var lexPage = PageRequest.of(0, mergeCap);
            var lexSlice = entityType != null && !entityType.isBlank()
                    ? searchIndexRepository.searchIdsByTextAndEntityType(tenantId, query, entityType, lexPage)
                    : searchIndexRepository.searchIdsByText(tenantId, query, lexPage);
            merged = PgvectorAnnSupport.mergeAnnThenLexical(annIds, lexSlice.getContent());
        }

        int poolMax = annCap + mergeCap;
        if (merged.size() > poolMax) {
            merged = merged.subList(0, poolMax);
        }

        List<SearchIndexEntity> pool = loadEntitiesInIdOrder(merged);
        applyRanking(query, mode, pool);

        int from = page * size;
        if (from >= pool.size()) {
            int totalPages = pool.isEmpty() ? 0 : (int) Math.ceil(pool.size() / (double) size);
            return new SearchResponse(List.of(), page, size, pool.size(), totalPages,
                    annRankModeApplied(mode));
        }
        int to = Math.min(from + size, pool.size());
        List<SearchIndexEntity> pageRows = new ArrayList<>(pool.subList(from, to));

        int totalPages = pool.isEmpty() ? 0 : (int) Math.ceil(pool.size() / (double) size);
        List<IndexResponse> results = pageRows.stream().map(this::toResponse).collect(Collectors.toList());
        return new SearchResponse(results, page, size, pool.size(), totalPages, annRankModeApplied(mode));
    }

    private static String annRankModeApplied(String mode) {
        if ("hybrid".equalsIgnoreCase(mode)) {
            return "hybrid-pgvector-ann";
        }
        return "semantic-pgvector-ann";
    }

    private List<SearchIndexEntity> loadEntitiesInIdOrder(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<SearchIndexEntity> found = searchIndexRepository.findAllById(ids);
        var byId = new HashMap<String, SearchIndexEntity>();
        for (SearchIndexEntity e : found) {
            byId.put(e.getId(), e);
        }
        List<SearchIndexEntity> ordered = new ArrayList<>(ids.size());
        for (String id : ids) {
            SearchIndexEntity e = byId.get(id);
            if (e != null) {
                ordered.add(e);
            }
        }
        return ordered;
    }

    @Transactional(readOnly = true)
    public SearchResponse listByType(String entityType, String tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "indexedAt"));
        Page<SearchIndexEntity> results = searchIndexRepository
                .findByTenantIdAndEntityType(tenantId, entityType, pageable);

        return toSearchResponse(null, results, page, size, null);
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

    private SearchResponse toSearchResponse(
            String query, Page<SearchIndexEntity> pageResult, int page, int size, String rankMode) {
        List<SearchIndexEntity> rows = new ArrayList<>(pageResult.getContent());
        String applied = applyRanking(query, rankMode, rows);

        List<IndexResponse> results = rows.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return new SearchResponse(results, page, size,
                pageResult.getTotalElements(), pageResult.getTotalPages(), applied);
    }

    private String applyRanking(String query, String rankMode, List<SearchIndexEntity> rows) {
        if (rankMode == null || query == null || query.isBlank() || rows.isEmpty()) {
            return null;
        }
        String mode = rankMode.trim();
        if (mode.equalsIgnoreCase("lexical")) {
            rows.sort(entityLexicalComparator(query));
            return "lexical";
        }
        if (mode.equalsIgnoreCase("semantic")) {
            return applySemanticRanking(query, rows);
        }
        if (mode.equalsIgnoreCase("hybrid")) {
            return applyHybridRanking(query, rows);
        }
        return null;
    }

    private String applySemanticRanking(String query, List<SearchIndexEntity> rows) {
        float[] q = embeddingModel.embed(EmbeddingVectors.truncate(query, embeddingsProperties.getMaxInputChars()));
        if (q == null) {
            rows.sort(entityLexicalComparator(query));
            return "semantic-proxy-lexical";
        }
        boolean anyDocVec = rows.stream().anyMatch(e -> e.getEmbeddingJson() != null && !e.getEmbeddingJson().isBlank());
        if (!anyDocVec) {
            rows.sort(entityLexicalComparator(query));
            return "semantic-proxy-lexical";
        }
        rows.sort(Comparator
                .comparingDouble((SearchIndexEntity e) -> semanticScore(q, query, e))
                .thenComparing(SearchIndexEntity::getIndexedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return "semantic-cosine";
    }

    private String applyHybridRanking(String query, List<SearchIndexEntity> rows) {
        float[] q = embeddingModel.embed(EmbeddingVectors.truncate(query, embeddingsProperties.getMaxInputChars()));
        int maxLex = rows.stream().mapToInt(e -> lexicalScore(query, toResponse(e))).max().orElse(1);
        double w = rankingProperties.getHybridCosineWeight();
        if (w < 0) {
            w = 0;
        } else if (w > 1) {
            w = 1;
        }
        final double wCos = w;
        if (q == null) {
            rows.sort(entityLexicalComparator(query));
            return "hybrid-proxy-lexical";
        }
        boolean anyDocVec = rows.stream().anyMatch(e -> e.getEmbeddingJson() != null && !e.getEmbeddingJson().isBlank());
        if (!anyDocVec) {
            rows.sort(entityLexicalComparator(query));
            return "hybrid-proxy-lexical";
        }
        final int maxLexFinal = Math.max(maxLex, 1);
        rows.sort(Comparator
                .comparingDouble((SearchIndexEntity e) -> hybridScore(q, query, e, maxLexFinal, wCos))
                .thenComparing(SearchIndexEntity::getIndexedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return "hybrid-cosine-lexical";
    }

    private double hybridScore(float[] q, String query, SearchIndexEntity e, int maxLex, double wCos) {
        IndexResponse ir = indexResponseForRanking(e);
        double lexN = lexicalScore(query, ir) / (double) maxLex;
        float[] d = EmbeddingVectors.fromJson(e.getEmbeddingJson());
        double cosN = 0.0;
        if (d != null) {
            double c = EmbeddingVectors.cosineAligned(q, d);
            if (!Double.isNaN(c)) {
                cosN = (c + 1.0) / 2.0;
            }
        }
        return wCos * cosN + (1.0 - wCos) * lexN;
    }

    private double semanticScore(float[] q, String query, SearchIndexEntity e) {
        float[] d = EmbeddingVectors.fromJson(e.getEmbeddingJson());
        if (d != null) {
            double c = EmbeddingVectors.cosineAligned(q, d);
            if (!Double.isNaN(c)) {
                return c;
            }
        }
        return lexicalScore(query, indexResponseForRanking(e)) / 1000.0;
    }

    private IndexResponse indexResponseForRanking(SearchIndexEntity entity) {
        return toResponse(entity);
    }

    private Comparator<SearchIndexEntity> entityLexicalComparator(String query) {
        return Comparator
                .comparingInt((SearchIndexEntity e) -> -lexicalScore(query, indexResponseForRanking(e)))
                .thenComparing(SearchIndexEntity::getIndexedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    /**
     * Token overlap heuristic — complements dense scores in hybrid mode.
     */
    private static int lexicalScore(String query, IndexResponse r) {
        String blob = r.rankingBlob();
        int score = 0;
        for (String term : query.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (term.length() < 2) {
                continue;
            }
            int idx = 0;
            while ((idx = blob.indexOf(term, idx)) >= 0) {
                score += 4;
                idx += term.length();
            }
        }
        if (r.tags() != null) {
            for (String tag : r.tags()) {
                if (tag != null && blob.contains(tag.toLowerCase(Locale.ROOT))) {
                    score += 2;
                }
            }
        }
        return score;
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
